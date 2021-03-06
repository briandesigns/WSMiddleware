package middleware;



import middleware.mwclient.MWClient;
import server.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import javax.jws.WebService;


@WebService(endpointInterface = "middleware.MWResourceManager")
public class MiddlewareImpl implements server.ws.ResourceManager {

    protected RMHashtable m_itemHT = new RMHashtable();
        protected MWClient flightClient;
        protected MWClient carClient;
        protected MWClient roomClient;

    public MiddlewareImpl() {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("RMList.txt").getFile());
            String[] address = new String[6];
            String line;
//            BufferedReader br = new BufferedReader(new FileReader("/home/brian/RMList.txt"));
            BufferedReader br = new BufferedReader(new FileReader(file));

            int i = 0;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split(" ");
                address[i]= tokens[0];
                address[i+1] = tokens[1];
                i=i+2;
            }

            flightClient = new MWClient("rm", address[0], Integer.parseInt(address[1]));
            carClient = new MWClient("rm", address[2], Integer.parseInt(address[3]));
            roomClient = new MWClient("rm", address[4], Integer.parseInt(address[5]));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    // Basic operations on RMItem //

    // Read a data item.
    private RMItem readData(int id, String key) {
        synchronized(m_itemHT) {
            return (RMItem) m_itemHT.get(key);
        }
    }

    // Write a data item.
    private void writeData(int id, String key, RMItem value) {
        synchronized(m_itemHT) {
            m_itemHT.put(key, value);
        }
    }

    // Remove the item out of storage.
    protected RMItem removeData(int id, String key) {
        synchronized(m_itemHT) {
            return (RMItem) m_itemHT.remove(key);
        }
    }


    // Basic operations on ReservableItem //

    // Delete the entire item.
    protected boolean deleteItem(int id, String key) {
        Trace.info("RM::deleteItem(" + id + ", " + key + ") called.");
        ReservableItem curObj = (ReservableItem) readData(id, key);
        // Check if there is such an item in the storage.
        if (curObj == null) {
            Trace.warn("RM::deleteItem(" + id + ", " + key + ") failed: "
                    + " item doesn't exist.");
            return false;
        } else {
            if (curObj.getReserved() == 0) {
                removeData(id, curObj.getKey());
                Trace.info("RM::deleteItem(" + id + ", " + key + ") OK.");
                return true;
            }
            else {
                Trace.info("RM::deleteItem(" + id + ", " + key + ") failed: "
                        + "some customers have reserved it.");
                return false;
            }
        }
    }

    // Query the number of available seats/rooms/cars.
    protected int queryNum(int id, String key) {
        Trace.info("RM::queryNum(" + id + ", " + key + ") called.");
        ReservableItem curObj = (ReservableItem) readData(id, key);
        int value = 0;
        if (curObj != null) {
            value = curObj.getCount();
        }
        Trace.info("RM::queryNum(" + id + ", " + key + ") OK: " + value);
        return value;
    }

    // Query the price of an item.
    protected int queryPrice(int id, String key) {
        Trace.info("RM::queryPrice(" + id + ", " + key + ") called.");
        ReservableItem curObj = (ReservableItem) readData(id, key);
        int value = 0;
        if (curObj != null) {
            value = curObj.getPrice();
        }
        Trace.info("RM::queryPrice(" + id + ", " + key + ") OK: $" + value);
        return value;
    }

    // Reserve an item.
    protected boolean reserveItem(int id, int customerId,
                                  String key, String location) throws Exception {
        Trace.info("RM::reserveItem(" + id + ", " + customerId + ", "
                + key + ", " + location + ") called.");
        // Read customer object if it exists (and read lock it).
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") failed: customer doesn't exist.");
            return false;
        }
        //Check for item availability and getting price
        MWClient proxy;
        boolean isSuccessfulReservation = false;
        int itemPrice = -1;
        if(key.contains("car-")) {
            proxy = this.carClient;
            isSuccessfulReservation = proxy.proxy.reserveCar(id, customerId, location);
            itemPrice = proxy.proxy.queryCarsPrice(id, location);
        } else if (key.contains("flight-")) {
            proxy = this.flightClient;
            isSuccessfulReservation = proxy.proxy.reserveFlight(id, customerId, Integer.parseInt(location));
            itemPrice = proxy.proxy.queryFlightPrice(id, Integer.parseInt(location));
        } else if (key.contains("room-")) {
            proxy = this.roomClient;
            isSuccessfulReservation = proxy.proxy.reserveRoom(id, customerId, location);
            itemPrice = proxy.proxy.queryRoomsPrice(id, location);
        } else {
            throw new Exception("can't reserve this");
        }
        // Check if the item is available.
        if (!isSuccessfulReservation) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") failed: item doesn't exist or no more items.");
            return false;
        } else {
            // Do reservation.

            cust.reserve(key, location, itemPrice);
            writeData(id, cust.getKey(), cust);

            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") OK.");
            return true;
        }
    }


    // Flight operations //

    // Create a new flight, or add seats to existing flight.
    // Note: if flightPrice <= 0 and the flight already exists, it maintains 
    // its current price.
    @Override
    public boolean addFlight(int id, int flightNumber,
                             int numSeats, int flightPrice) {
        return flightClient.proxy.addFlight(id, flightNumber, numSeats, flightPrice);
    }

    @Override
    public boolean deleteFlight(int id, int flightNumber) {
        return flightClient.proxy.deleteFlight(id, flightNumber);
    }

    // Returns the number of empty seats on this flight.
    @Override
    public int queryFlight(int id, int flightNumber) {
        return flightClient.proxy.queryFlight(id, flightNumber);
    }

    // Returns price of this flight.
    public int queryFlightPrice(int id, int flightNumber) {
        return flightClient.proxy.queryFlightPrice(id, flightNumber);
    }

    /*
    // Returns the number of reservations for this flight. 
    public int queryFlightReservations(int id, int flightNumber) {
        Trace.info("RM::queryFlightReservations(" + id 
                + ", #" + flightNumber + ") called.");
        RMInteger numReservations = (RMInteger) readData(id, 
                Flight.getNumReservationsKey(flightNumber));
        if (numReservations == null) {
            numReservations = new RMInteger(0);
       }
        Trace.info("RM::queryFlightReservations(" + id + 
                ", #" + flightNumber + ") = " + numReservations);
        return numReservations.getValue();
    }
    */
    
    /*
    // Frees flight reservation record. Flight reservation records help us 
    // make sure we don't delete a flight if one or more customers are 
    // holding reservations.
    public boolean freeFlightReservation(int id, int flightNumber) {
        Trace.info("RM::freeFlightReservations(" + id + ", " 
                + flightNumber + ") called.");
        RMInteger numReservations = (RMInteger) readData(id, 
                Flight.getNumReservationsKey(flightNumber));
        if (numReservations != null) {
            numReservations = new RMInteger(
                    Math.max(0, numReservations.getValue() - 1));
        }
        writeData(id, Flight.getNumReservationsKey(flightNumber), numReservations);
        Trace.info("RM::freeFlightReservations(" + id + ", " 
                + flightNumber + ") OK: reservations = " + numReservations);
        return true;
    }
    */


    // Car operations //

    // Create a new car location or add cars to an existing location.
    // Note: if price <= 0 and the car location already exists, it maintains 
    // its current price.
    @Override
    public boolean addCars(int id, String location, int numCars, int carPrice) {
        return carClient.proxy.addCars(id, location, numCars, carPrice);
    }

    // Delete cars from a location.
    @Override
    public boolean deleteCars(int id, String location) {
        return carClient.proxy.deleteCars(id, location);
    }

    // Returns the number of cars available at a location.
    @Override
    public int queryCars(int id, String location) {
        return carClient.proxy.queryCars(id, location);
    }

    // Returns price of cars at this location.
    @Override
    public int queryCarsPrice(int id, String location) {
        return carClient.proxy.queryCarsPrice(id, location);
    }


    // Room operations //

    // Create a new room location or add rooms to an existing location.
    // Note: if price <= 0 and the room location already exists, it maintains 
    // its current price.
    @Override
    public boolean addRooms(int id, String location, int numRooms, int roomPrice) {
        return roomClient.proxy.addRooms(id, location, numRooms, roomPrice);
    }

    // Delete rooms from a location.
    @Override
    public boolean deleteRooms(int id, String location) {
        return roomClient.proxy.deleteRooms(id, location);
    }

    // Returns the number of rooms available at a location.
    @Override
    public int queryRooms(int id, String location) {
        return roomClient.proxy.queryRooms(id, location);
    }

    // Returns room price at this location.
    @Override
    public int queryRoomsPrice(int id, String location) {
        return roomClient.proxy.queryRoomsPrice(id, location);
    }


    // Customer operations //

    @Override
    public int newCustomer(int id) {
        Trace.info("INFO: RM::newCustomer(" + id + ") called.");
        // Generate a globally unique Id for the new customer.
        int customerId = Integer.parseInt(String.valueOf(id) +
                String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
                String.valueOf(Math.round(Math.random() * 100 + 1)));
        Customer cust = new Customer(customerId);
        writeData(id, cust.getKey(), cust);
        Trace.info("RM::newCustomer(" + id + ") OK: " + customerId);
        return customerId;
    }

    // This method makes testing easier.
    @Override
    public boolean newCustomerId(int id, int customerId) {
        Trace.info("INFO: RM::newCustomer(" + id + ", " + customerId + ") called.");
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            cust = new Customer(customerId);
            writeData(id, cust.getKey(), cust);
            Trace.info("INFO: RM::newCustomer(" + id + ", " + customerId + ") OK.");
            return true;
        } else {
            Trace.info("INFO: RM::newCustomer(" + id + ", " +
                    customerId + ") failed: customer already exists.");
            return false;
        }
    }

    // Delete customer from the database. 
    @Override
    public boolean deleteCustomer(int id, int customerId) {
        Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") called.");
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.warn("RM::deleteCustomer(" + id + ", "
                    + customerId + ") failed: customer doesn't exist.");
            return false;
        } else {
            // Increase the reserved numbers of all reservable items that 
            // the customer reserved.
            boolean reservableItemUpdated = false;
            RMHashtable reservationHT = cust.getReservations();
            for (Enumeration e = reservationHT.keys(); e.hasMoreElements();) {
                String reservedKey = (String) (e.nextElement());
                ReservedItem reservedItem = cust.getReservedItem(reservedKey);
                Trace.info("RM::deleteCustomer(" + id + ", " + customerId + "): "
                        + "deleting " + reservedItem.getCount() + " reservations "
                        + "for item " + reservedItem.getKey());
                if(reservedItem.getKey().contains("flight-")) {
                    reservableItemUpdated = flightClient.proxy.increaseReservableItemCount(id, reservedItem.getKey(), reservedItem.getCount());
                } else if (reservedItem.getKey().contains("car-")) {
                    reservableItemUpdated = carClient.proxy.increaseReservableItemCount(id, reservedItem.getKey(), reservedItem.getCount());

                } else if (reservedItem.getKey().contains("room")) {
                    reservableItemUpdated = roomClient.proxy.increaseReservableItemCount(id, reservedItem.getKey(), reservedItem.getCount());

                } else {
                    Trace.info("reserved item does not exist");
                }

            }
            // Remove the customer from the storage.
            if (reservableItemUpdated){
                removeData(id, cust.getKey());
                Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") OK.");
                return true;
            } else {
                Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") failed. could not update one o the reservable items");
                return false;
            }

        }
    }

    // Return data structure containing customer reservation info. 
    // Returns null if the customer doesn't exist. 
    // Returns empty RMHashtable if customer exists but has no reservations.
    public RMHashtable getCustomerReservations(int id, int customerId) {
        Trace.info("RM::getCustomerReservations(" + id + ", "
                + customerId + ") called.");
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.info("RM::getCustomerReservations(" + id + ", "
                    + customerId + ") failed: customer doesn't exist.");
            return null;
        } else {
            return cust.getReservations();
        }
    }

    // Return a bill.
    @Override
    public String queryCustomerInfo(int id, int customerId) {
        Trace.info("RM::queryCustomerInfo(" + id + ", " + customerId + ") called.");
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.warn("RM::queryCustomerInfo(" + id + ", "
                    + customerId + ") failed: customer doesn't exist.");
            // Returning an empty bill means that the customer doesn't exist.
            return "";
        } else {
            String s = cust.printBill();
            Trace.info("RM::queryCustomerInfo(" + id + ", " + customerId + "): \n");
            System.out.println(s);
            return s;
        }
    }

    // Add flight reservation to this customer.  
    @Override
    public boolean reserveFlight(int id, int customerId, int flightNumber) {
        try {
            return reserveItem(id, customerId,
                    Flight.getKey(flightNumber), String.valueOf(flightNumber));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // Add car reservation to this customer. 
    @Override
    public boolean reserveCar(int id, int customerId, String location) {
        try {
            return reserveItem(id, customerId, Car.getKey(location), location);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // Add room reservation to this customer. 
    @Override
    public boolean reserveRoom(int id, int customerId, String location) {
        try {
            return reserveItem(id, customerId, Room.getKey(location), location);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    // Reserve an itinerary.
    @Override
    public boolean reserveItinerary(int id, int customerId, Vector flightNumbers,
                                    String location, boolean car, boolean room) {
        Iterator it = flightNumbers.iterator();

        boolean isSuccessfulReservation = false;
        while(it.hasNext()){
            try {
                isSuccessfulReservation = reserveFlight(id, customerId, getInt(it.next()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if(car) isSuccessfulReservation = reserveCar(id, customerId, location);
        if(room) isSuccessfulReservation = reserveRoom(id, customerId, location);
        return isSuccessfulReservation;
    }

    @Override
    public boolean increaseReservableItemCount(int id, String key, int Count) {
        return false;
    }

    public int getInt(Object temp) throws Exception {
        try {
            return (new Integer((String)temp)).intValue();
        }
        catch(Exception e) {
            throw e;
        }
    }


}
