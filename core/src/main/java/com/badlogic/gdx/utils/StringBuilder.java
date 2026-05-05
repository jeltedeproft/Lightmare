package com.badlogic.gdx.utils;

import java.io.Serializable;

/**
 * Compatibility class for box2dlights and other libraries that still expect 
 * com.badlogic.gdx.utils.StringBuilder in newer libGDX versions.
 */
public class StringBuilder implements Serializable, CharSequence, Appendable {
    private final java.lang.StringBuilder sb;

    public StringBuilder() {
        sb = new java.lang.StringBuilder();
    }

    public StringBuilder(int capacity) {
        sb = new java.lang.StringBuilder(capacity);
    }

    public StringBuilder(String str) {
        sb = new java.lang.StringBuilder(str);
    }

    public StringBuilder(CharSequence seq) {
        sb = new java.lang.StringBuilder(seq);
    }

    @Override
    public int length() {
        return sb.length();
    }

    @Override
    public char charAt(int index) {
        return sb.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return sb.subSequence(start, end);
    }

    @Override
    public StringBuilder append(CharSequence csq) {
        sb.append(csq);
        return this;
    }

    @Override
    public StringBuilder append(CharSequence csq, int start, int end) {
        sb.append(csq, start, end);
        return this;
    }

    @Override
    public StringBuilder append(char c) {
        sb.append(c);
        return this;
    }

    public StringBuilder append(String str) {
        sb.append(str);
        return this;
    }

    public StringBuilder append(Object obj) {
        sb.append(obj);
        return this;
    }

    public StringBuilder append(int i) {
        sb.append(i);
        return this;
    }

    public StringBuilder append(long l) {
        sb.append(l);
        return this;
    }

    public StringBuilder append(float f) {
        sb.append(f);
        return this;
    }

    public StringBuilder append(double d) {
        sb.append(d);
        return this;
    }

    public StringBuilder append(boolean b) {
        sb.append(b);
        return this;
    }

    public StringBuilder append(char[] str, int offset, int len) {
        sb.append(str, offset, len);
        return this;
    }

    public StringBuilder append(char[] str) {
        sb.append(str);
        return this;
    }

    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        sb.getChars(srcBegin, srcEnd, dst, dstBegin);
    }

    public void setLength(int newLength) {
        sb.setLength(newLength);
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
