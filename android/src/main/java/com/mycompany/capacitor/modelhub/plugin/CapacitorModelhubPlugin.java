package com.mycompany.capacitor.modelhub.plugin;

import com.getcapacitor.Logger;

public class CapacitorModelhubPlugin {

    public String echo(String value) {
        Logger.info("Echo", value);
        return value;
    }
}
