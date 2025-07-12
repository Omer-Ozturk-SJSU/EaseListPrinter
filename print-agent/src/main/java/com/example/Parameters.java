package com.example;

public class Parameters {
    private static volatile String printerName = null;
    private static volatile float pageWidthInch = 2f;
    private static volatile float pageHeightInch = 1f;
    private static volatile float marginXInch = 0f;
    private static volatile float marginYInch = 0f;
    private static volatile String orientation = "portrait";

    public static void setPrinterName(String name) {
        printerName = name;
    }

    public static String getPrinterName() {
        return printerName;
    }

    public static void setPageWidth(float w) {
        pageWidthInch = w;
    }

    public static void setPageHeight(float h) {
        pageHeightInch = h;
    }

    public static void setMargins(float mx, float my) {
        marginXInch = mx;
        marginYInch = my;
    }

    /**
     * @param o either "portrait" or "landscape"
     */
    public static void setOrientation(String o) {
        orientation = o.toLowerCase();
    }

    public static float getPageWidth() {
        return pageWidthInch;
    }

    public static float getPageHeight() {
        return pageHeightInch;
    }

    public static float getMarginX() {
        return marginXInch;
    }

    public static float getMarginY() {
        return marginYInch;
    }

    public static String getOrientation() {
        return orientation;
    }
}
