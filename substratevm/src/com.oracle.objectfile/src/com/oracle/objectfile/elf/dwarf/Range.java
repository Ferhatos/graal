package com.oracle.objectfile.elf.dwarf;

// details of a specific address range in a compiled method
// either a primary range identifying a whole method
// or a sub-range identifying a sequence of
// instructions that belong to an inlined method

public class Range {
    private String fileName;
    private String className;
    private String methodName;
    private String paramNames;
    private String returnTypeName;
    private String fullMethodName;
    private int lo;
    private int hi;
    private int line;
    // this is null for a primary range
    private Range primary;

    // create a primary range
    Range(String fileName, String className, String methodName, String paramNames, String returnTypeName, StringTable stringTable, int lo, int hi, int line) {
        this(fileName, className, methodName, paramNames, returnTypeName, stringTable, lo, hi, line, null);
    }

    // create a primary or secondary range
    Range(String fileName, String className, String methodName, String paramNames, String returnTypeName, StringTable stringTable, int lo, int hi, int line, Range primary) {
        // currently file name and full method name need to go into the debug_str section
        // other strings just need to be deduplicated to save space
        this.fileName = stringTable.uniqueDebugString(fileName);
        this.className = stringTable.uniqueString(className);
        this.methodName = stringTable.uniqueString(methodName);
        this.paramNames = stringTable.uniqueString(paramNames);
        this.returnTypeName = stringTable.uniqueString(returnTypeName);
        this.fullMethodName = stringTable.uniqueDebugString(getClassAndMethodNameWithParams());
        this.lo = lo;
        this.hi = hi;
        this.line = line;
        this.primary = primary;
    }


    public boolean sameClassName(Range other) {
          return className.equals(other.className);
    }

    public boolean sameMethodName(Range other) {
          return methodName.equals(other.methodName);
    }

    public boolean sameParamNames(Range other) {
          return paramNames.equals(other.paramNames);
    }

    public boolean sameReturnTypeName(Range other) {
          return returnTypeName.equals(other.returnTypeName);
    }

    public boolean sameFileName(Range other) {
          return fileName.equals(other.fileName);
    }

    public boolean sameMethod(Range other) {
          return sameClassName(other) &&
                  sameMethodName(other) &&
                  sameParamNames(other) &&
                  sameReturnTypeName(other);
    }

    public boolean contains(Range other) {
        return (lo <= other.lo && hi >= other.hi);
    }

    public boolean isPrimary() {
        return getPrimary() == null;
    }

    public Range getPrimary() {
        return primary;
    }

    public String getFileName() {
        return fileName;
    }
    public String getClassName() {
        return className;
    }
    public String getMethodName() {
        return methodName;
    }
    public String getParamNames() {
        return paramNames;
    }
    public String getReturnTypeName() {
        return returnTypeName;
    }
    public int getHi() {
        return hi;
    }
    public int getLo() {
        return lo;
    }
    public int getLine() {
        return line;
    }
    public String getClassAndMethodName() {
        return getExtendedMethodName(false, false);
    }
    public String getClassAndMethodNameWithParams() {
        return getExtendedMethodName(true, false);
    }

    public String getFullMethodName() {
        return  getExtendedMethodName(true, true);
    }

    public String getExtendedMethodName(boolean includeParams, boolean includeReturnType) {
        StringBuilder builder = new StringBuilder();
        if (includeReturnType && returnTypeName.length() > 0) {
            builder.append(returnTypeName);
            builder.append(' ');
        }
        if (className != null) {
            builder.append(className);
            builder.append("::");
        }
        builder.append(methodName);
        if (includeParams && !paramNames.isEmpty()) {
            builder.append('(');
            builder.append(paramNames);
            builder.append(')');
        }
        return builder.toString();
    }
}
