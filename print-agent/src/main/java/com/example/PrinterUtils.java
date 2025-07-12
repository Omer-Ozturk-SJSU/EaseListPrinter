package com.example;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.util.Arrays;

public class PrinterUtils {
    /**
     * Returns the names of all printers available on this system.
     */
    public static String[] listPrinters() {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        return Arrays.stream(services)
                .map(PrintService::getName)
                .toArray(String[]::new);
    }

    /**
     * Finds a printer by its name.
     *
     * @param name the name of the printer
     * @return the PrintService representing the printer, or null if not found
     */
    public static javax.print.PrintService findPrinterByName(String name) {
        javax.print.PrintService[] services = javax.print.PrintServiceLookup.lookupPrintServices(null, null);
        for (javax.print.PrintService svc : services) {
            if (svc.getName().equalsIgnoreCase(name)) {
                return svc;
            }
        }
        return null;
    }
}