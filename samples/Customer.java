package tracking.pkgnull.check;

import java.util.ArrayList;
import java.util.Scanner;

public class Customer {

    String name;
    Address adress;
    ArrayList<Customer> customers;

    public Customer() {
        name = null;
        adress = null;
        customers = null;
    }

    public Customer(String name, String city, String street) {
        this.name = name;
        this.adress = new Address(city, street);
        customers = new ArrayList<>();
    }

    public void printInfo() {
        if ((name == null) || (adress == null)) {
        } else {
            System.out.println("Name : " + name + "\n" + "Address : " + adress);
            printCustomersList();
        }
    }

    public void printCustomersList() {
        if (customers != null) {
            for (Customer c : customers) {
                if ((c.name != null) && (c.adress != null)) {
                    System.out.println(c);
                }
            }
        }
    }

    public void AddCustomers(Customer c) {
        if (c != null) {
            customers.add(c);
        }
    }

    public Customer getCustomerbyName(String name) {
        if (customers != null) {
            for (int i = 0; i < customers.size(); i++) {
                if ((customers.get(i).name.equals(name)) && (customers.get(i) != null)) {
                    return customers.get(i);
                }
            }
        }
        return null;
    }

    public Address changeAddress(String newCity, String newStreet) {
        if ((newCity != null) && (newStreet != null)) {
            return new Address(newCity, newStreet);
        }
        return null;
    }

    public static void main(String args) {
        Scanner in = new Scanner(System.in);
        while (true) {
            String cName = in.next();
            String cAddress = in.next();
            String cStreet = in.next();
            boolean b = false;
            Customer customer1;
            Customer customer2;
            if ((cName == null) || (cAddress == null) || (cStreet == null)) {
                System.out.println("Cannot add null customer");
                break;
            } else {
                customer1 = new Customer(cName, cAddress, cStreet);
                customer2 = new Customer();
                Object obj = (String) customer2.name;
                if ((obj != null) && (customer2.adress != null)) {
                    if (b) {
                        customer1.AddCustomers(customer2);
                        b = false;
                    }
                }
                if (customer2.getCustomerbyName(cName) == null) {
                    break;
                } else {
                    if (!b) {
                        System.out.println(customer2.getCustomerbyName(cName));
                    }
                }
            }
            String city2 = in.next();
            String Street2 = in.next();
            if ((null == city2) || (Street2 == null)) {
                break;
            } else {
                Address ad = customer1.changeAddress(city2, cStreet);
                if (ad.city.equals("syria")) {
                    if (ad != null) {
                        System.out.println("address changed succeful");
                    }
                }
            }
            customer1.printInfo();
            customer2.printInfo();
        }
    }
}
