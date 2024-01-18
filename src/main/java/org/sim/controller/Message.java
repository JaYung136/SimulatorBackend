package org.sim.controller;

public class Message {
    public CODE code;
    public Object message;

    public static Message Success(Object message) {
        Message m = new Message();
        m.code = CODE.SUCCESS;
        m.message = message;
        return m;
    }

    public static Message Fail(Object message) {
        Message m = new Message();
        m.code = CODE.FAILED;
        m.message = message;
        return m;
    }
}
