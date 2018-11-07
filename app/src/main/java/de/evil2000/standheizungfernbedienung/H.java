package de.evil2000.standheizungfernbedienung;

/**
 * Created by dave on 08.12.17.
 */

public class H {
    /**
     * Get the method name for a depth in call stack.
     *
     * @param c The class which includes the currently executed function.
     * @return method name
     */
    public static String thisFunc(Class<?> c) {
        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        for (StackTraceElement e : ste) {
            if (c.getCanonicalName().equals(e.getClassName())) {
                //return e.getMethodName();
                return e.getClassName() + "." + e.getMethodName() + "(" + e.getFileName() + ":" +
                        e.getLineNumber() + ")";
            }
            //Log.d("__FUNC__()", e.getClassName() + " " + e.getMethodName() + " (" + e.getFileName() + ":" + e.getLineNumber() + ")");
        }
        return "";
    }
}
