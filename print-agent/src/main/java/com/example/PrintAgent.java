package com.example;

import com.sun.net.httpserver.*;

import java.awt.*; // SystemTray, TrayIcon, PopupMenu, MenuItem, Toolkit
import java.awt.event.*; // ActionListener
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterJob;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;

import java.util.Set;

public class PrintAgent {
    public static void main(String[] args) throws IOException {
        // Self-diagnostic logging
        System.out.println("HEADLESS? " + java.awt.GraphicsEnvironment.isHeadless());
        System.out.println("TRAY SUPPORTED? " + java.awt.SystemTray.isSupported());

        final HttpServer[] server = new HttpServer[1];
        try {
            server[0] = HttpServer.create(new InetSocketAddress(9999), 0);
            server[0].start();
            System.out.println("HTTP server listening on port 9999");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to start HTTP server");
            return;
        }

        // /setprinter endpoint
        server[0].createContext("/setprinter", exchange -> {
            System.out.println("[API] /setprinter " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                handleCorsPreflight(exchange);
                return;
            }

            if (!isCorsAllowed(exchange)) {
                System.out.println(
                        "[API] /setprinter CORS denied for origin: " + exchange.getRequestHeaders().getFirst("Origin"));
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                return;
            }

            applyCorsHeaders(exchange); // adds Access-Control-Allow-Origin

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("[API] /setprinter body: " + body);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("printer")) {
                String selected = json.get("printer").getAsString();
                Parameters.setPrinterName(selected);
                System.out.println("[API] Printer set to: " + selected);
                String resp = "Printer set to: " + selected;
                exchange.sendResponseHeaders(200, resp.getBytes().length);
                exchange.getResponseBody().write(resp.getBytes());
            } else {
                System.out.println("[API] /setprinter missing 'printer' field");
                String resp = "Missing 'printer' field";
                exchange.sendResponseHeaders(400, resp.getBytes().length);
                exchange.getResponseBody().write(resp.getBytes());
            }
            exchange.close();
        });

        // /setparam endpoint
        server[0].createContext("/setparam", exchange -> {
            System.out.println("[API] /setparam " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                handleCorsPreflight(exchange);
                return;
            }

            if (!isCorsAllowed(exchange)) {
                System.out.println(
                        "[API] /setparam CORS denied for origin: " + exchange.getRequestHeaders().getFirst("Origin"));
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                return;
            }

            applyCorsHeaders(exchange); // adds Access-Control-Allow-Origin

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("[API] /setparam body: " + body);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            // read size & margins
            float w = json.has("width") ? json.get("width").getAsFloat() : Parameters.getPageWidth();
            float h = json.has("height") ? json.get("height").getAsFloat() : Parameters.getPageHeight();
            float mx = json.has("marginX") ? json.get("marginX").getAsFloat() : Parameters.getMarginX();
            float my = json.has("marginY") ? json.get("marginY").getAsFloat() : Parameters.getMarginY();

            // read orientation
            if (json.has("orientation")) {
                Parameters.setOrientation(json.get("orientation").getAsString());
            }

            // apply
            Parameters.setPageWidth(w);
            Parameters.setPageHeight(h);
            // Parameters.setMargins(mx, my); // [REMOVED: margin setting temporarily
            // disabled]
            System.out.printf("[API] Params set: width=%.2f, height=%.2f, marginX=%.2f, marginY=%.2f\n", w, h, mx, my);
            String resp = String.format("Params set W=%.2f H=%.2f MX=%.2f MY=%.2f", w, h, mx, my);
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            exchange.getResponseBody().write(resp.getBytes());
            exchange.close();
        });

        // /print now handles images
        server[0].createContext("/print", exchange -> {
            System.out.println("[API] /print " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                handleCorsPreflight(exchange);
                return;
            }

            applyCorsHeaders(exchange);

            byte[] data = exchange.getRequestBody().readAllBytes();
            BufferedImage img = null;
            if (data.length == 0) {
                System.out.println("[API] /print no data — using internal test image");
                InputStream testImg = PrintAgent.class.getResourceAsStream("/test-print.png");
                if (testImg == null) {
                    System.err.println("test-print.png not found in classpath");
                    String resp = "Missing embedded test-print.png";
                    exchange.sendResponseHeaders(500, resp.length());
                    exchange.getResponseBody().write(resp.getBytes());
                    exchange.close();
                    return;
                }
                img = javax.imageio.ImageIO.read(testImg);
                testImg.close();
            } else {
                img = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
            }
            if (img == null) {
                String resp = "Invalid image format";
                exchange.sendResponseHeaders(400, resp.length());
                exchange.getResponseBody().write(resp.getBytes());
                exchange.close();
                return;
            }

            try {
                float w = Parameters.getPageWidth();
                float h = Parameters.getPageHeight();
                float mx = Parameters.getMarginX();
                float my = Parameters.getMarginY();

                String name = Parameters.getPrinterName();
                javax.print.PrintService svc;
                if (name != null) {
                    svc = PrinterUtils.findPrinterByName(name);
                    if (svc == null) {
                        svc = javax.print.PrintServiceLookup.lookupDefaultPrintService();
                        System.out.println("[API] Printer '" + name + "' not found; using default");
                    }
                } else {
                    svc = javax.print.PrintServiceLookup.lookupDefaultPrintService();
                }

                printImageScaled(img, svc, w, h, mx, my);

                String resp = "Image printed";
                exchange.sendResponseHeaders(200, resp.length());
                exchange.getResponseBody().write(resp.getBytes());
            } catch (Exception e) {
                String resp = "Failed to print image: " + e.getMessage();
                exchange.sendResponseHeaders(500, resp.length());
                exchange.getResponseBody().write(resp.getBytes());
            }
            exchange.close();
        });

        // /print-pdf now handles PDFs
        server[0].createContext("/print-pdf", exchange -> {
            System.out.println("[API] /print-pdf " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                handleCorsPreflight(exchange);
                return;
            }

            if (!isCorsAllowed(exchange)) {
                System.out.println(
                        "[API] /print-pdf CORS denied for origin: " + exchange.getRequestHeaders().getFirst("Origin"));
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                return;
            }

            applyCorsHeaders(exchange); // adds Access-Control-Allow-Origin

            byte[] data = exchange.getRequestBody().readAllBytes();
            System.out.println("[API] /print-pdf got " + data.length + " bytes");
            if (data.length == 0) {
                System.out.println("[API] /print-pdf no data — using internal test PDF");
                InputStream testPdf = PrintAgent.class.getResourceAsStream("/test-print.pdf");
                if (testPdf == null) {
                    System.err.println("test-print.pdf not found in classpath");
                    String resp = "Missing embedded test-print.pdf";
                    exchange.sendResponseHeaders(500, resp.length());
                    exchange.getResponseBody().write(resp.getBytes());
                    exchange.close();
                    return;
                }
                data = testPdf.readAllBytes();
                testPdf.close();
            }

            String name = Parameters.getPrinterName();
            javax.print.PrintService svc;
            if (name != null) {
                svc = PrinterUtils.findPrinterByName(name);
                if (svc == null) {
                    svc = javax.print.PrintServiceLookup.lookupDefaultPrintService();
                    System.out.println("[API] Printer '" + name + "' not found; using default");
                }
            } else {
                svc = javax.print.PrintServiceLookup.lookupDefaultPrintService();
            }

            float wIn = Parameters.getPageWidth();
            float hIn = Parameters.getPageHeight();
            float mxIn = Parameters.getMarginX();
            float myIn = Parameters.getMarginY();

            try {
                printPdfScaled(data, svc, wIn, hIn, mxIn, myIn);
                System.out.println("[API] Print job sent to printer");
                String response = "Printed";
                exchange.sendResponseHeaders(200, response.length());
                exchange.getResponseBody().write(response.getBytes());
            } catch (Exception e) {
                System.out.println("[API] Print failed: " + e.getMessage());
                String response = "Print failed: " + e.getMessage();
                exchange.sendResponseHeaders(500, response.length());
                exchange.getResponseBody().write(response.getBytes());
            }
            exchange.close();
        });

        server[0].createContext("/printers", exchange -> {
            System.out.println("[API] /printers " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                handleCorsPreflight(exchange);
                return;
            }

            if (!isCorsAllowed(exchange)) {
                System.out.println(
                        "[API] /printers CORS denied for origin: " + exchange.getRequestHeaders().getFirst("Origin"));
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                return;
            }

            applyCorsHeaders(exchange); // adds Access-Control-Allow-Origin

            String[] printers = PrinterUtils.listPrinters();
            String json = new com.google.gson.Gson().toJson(printers);
            byte[] resp = json.getBytes("UTF-8");

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");

            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            System.out.println("[API] /printers responded with " + printers.length + " printers");
            exchange.close();
        });
        System.out.println("Registered /printers handler");

        // System tray integration
        if (SystemTray.isSupported()) {
            // 1) Load your icon
            Image image = Toolkit.getDefaultToolkit()
                    .getImage(PrintAgent.class.getResource("/printer.png"));
            // 2) Create a popup menu with one item “Exit”
            PopupMenu popup = new PopupMenu();
            MenuItem exitItem = new MenuItem("Exit");
            popup.add(exitItem);
            // 3) Create the TrayIcon
            TrayIcon trayIcon = new TrayIcon(image, "EasePrintAgent", popup);
            trayIcon.setImageAutoSize(true);
            // 4) Add it to the SystemTray
            SystemTray tray = SystemTray.getSystemTray();
            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                System.err.println("Unable to add tray icon: " + e.getMessage());
            }
            // 5) Wire the Exit action
            exitItem.addActionListener(evt -> {
                server[0].stop(0);
                tray.remove(trayIcon);
                System.exit(0);
            });
        } else {
            System.out.println("System tray not supported on this platform.");
        }
    }

    // Print PDF scaled to fit 2x1 inch label using PDFBox
    public static void printPdfScaled(
            byte[] pdfBytes,
            javax.print.PrintService svc,
            float widthInches,
            float heightInches,
            float marginXInches,
            float marginYInches) throws Exception {

        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(svc);

            PageFormat pf = job.defaultPage();
            Paper paper = pf.getPaper();

            double w = widthInches * 72;
            double h = heightInches * 72;
            paper.setSize(w, h);

            paper.setImageableArea(
                    marginXInches * 72,
                    marginYInches * 72,
                    (widthInches - 2 * marginXInches) * 72,
                    (heightInches - 2 * marginYInches) * 72);

            pf.setPaper(paper);

            PDFPrintable printable = new PDFPrintable(doc, Scaling.SCALE_TO_FIT);
            job.setPrintable(printable, pf);

            PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
            attrs.add(new MediaPrintableArea(
                    0f, 0f, widthInches, heightInches, MediaPrintableArea.INCH));

            // 📐 Automatically set orientation
            // honor user’s choice
            if ("landscape".equalsIgnoreCase(Parameters.getOrientation())) {
                attrs.add(OrientationRequested.LANDSCAPE);
            } else {
                attrs.add(OrientationRequested.PORTRAIT);
            }

            System.out.printf("Label size: %.2f in x %.2f in (W x H)%n", widthInches, heightInches);

            job.print(attrs);
        }
    }

    public static void printImageScaled(
            java.awt.image.BufferedImage image,
            javax.print.PrintService svc,
            float widthInches,
            float heightInches,
            float marginXInches,
            float marginYInches) throws Exception {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(svc);

        PageFormat pf = job.defaultPage();
        Paper paper = pf.getPaper();

        double w = widthInches * 72;
        double h = heightInches * 72;
        paper.setSize(w, h);
        paper.setImageableArea(
                marginXInches * 72,
                marginYInches * 72,
                (widthInches - 2 * marginXInches) * 72,
                (heightInches - 2 * marginYInches) * 72);
        pf.setPaper(paper);

        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            // Only one page — pageIndex 0
            if (pageIndex > 0) {
                return java.awt.print.Printable.NO_SUCH_PAGE;
            }
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) graphics;
            g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            g2.drawImage(image, 0, 0,
                    (int) pageFormat.getImageableWidth(),
                    (int) pageFormat.getImageableHeight(),
                    null);
            return java.awt.print.Printable.PAGE_EXISTS;
        }, pf);

        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
        // honor user’s choice
        if ("landscape".equalsIgnoreCase(Parameters.getOrientation())) {
            attrs.add(OrientationRequested.LANDSCAPE);
        } else {
            attrs.add(OrientationRequested.PORTRAIT);
        }
        attrs.add(new MediaPrintableArea(
                0f, 0f, widthInches, heightInches, MediaPrintableArea.INCH));
        job.print(attrs);
    }

    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "http://localhost:3000",
            "https://easelist.ai" // Replace with prod domain later ***
    );

    private static boolean isCorsAllowed(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        return origin != null && ALLOWED_ORIGINS.contains(origin);
    }

    private static void applyCorsHeaders(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", origin);
            headers.add("Vary", "Origin");
        }
    }

    private static void handleCorsPreflight(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        applyCorsHeaders(exchange);
        headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1); // No content
        exchange.close();
    }
}