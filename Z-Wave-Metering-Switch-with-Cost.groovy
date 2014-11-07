metadata {
    // Automatically generated. Make future change here.
    definition (name: "Test Z-Wave Metering Switch", author: "Joe da Silva") {
        capability "Energy Meter"
        capability "Actuator"
        capability "Switch"
        capability "Power Meter"
        capability "Polling"
        capability "Refresh"
        capability "Sensor"

        attribute "energyCost", "string"  

        command "reset"

        fingerprint inClusters: "0x25,0x32"
    }

    // simulator metadata
    simulator {
        status "on":  "command: 2003, payload: FF"
        status "off": "command: 2003, payload: 00"

        for (int i = 0; i <= 10000; i += 1000) {
            status "power  ${i} W": new physicalgraph.zwave.Zwave().meterV1.meterReport(
                scaledMeterValue: i, precision: 3, meterType: 4, scale: 2, size: 4).incomingMessage()
        }
        for (int i = 0; i <= 100; i += 10) {
            status "energy  ${i} kWh": new physicalgraph.zwave.Zwave().meterV1.meterReport(
                scaledMeterValue: i, precision: 3, meterType: 0, scale: 0, size: 4).incomingMessage()
        }

        // reply messages
        reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
        reply "200100,delay 100,2502": "command: 2503, payload: 00"

    }

    // tile definitions
    tiles {
        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821"
            state "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
        }
        valueTile("energyCost", "device.energyCost") {
            state "default", label: '${currentValue}'//, foregroundColor: "#000000", backgroundColor:"#ffffff") //suggested by storageanarchy
        }
        valueTile("power", "device.power", decoration: "flat") {
            state "default", label:'${currentValue} W'
        }
        valueTile("energy", "device.energy", decoration: "flat") {
            state "default", label:'${currentValue} kWh'
        }
        standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat") {
            state "default", label:'reset kWh', action:"reset"
        }
        standardTile("configure", "device.power", inactiveLabel: false, decoration: "flat") {
            state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
        }
        standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat") {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main (["switch","energyCost"])
        details(["switch","energyCost","power","energy","reset","configure","refresh"])
        }
        preferences {
             input "kWhCost", "string", title: "\$/kWh (0.16)", defaultValue: "0.16" as String
        }
}

def parse(String description) {
    def result = null
    def cmd = zwave.parse(description, [0x20: 1, 0x32: 1])
    if (cmd) {
        result = createEvent(zwaveEvent(cmd))
    }
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.meterv1.MeterReport cmd) {

    def dispValue
    def newValue

        if (cmd.scale == 0) {
            newValue = cmd.scaledMeterValue
        }
    else if (newValue != state.energyValue) {       //Reference from Aeon_HEMv2.groovy by Barry A. Burke 10-07-2014 "https://github.com/SANdood/Aeon-HEM-v2"
                dispValue = String.format("%5.2f",newValue)+"\nkWh"
                sendEvent(name: "energyDisp", value: dispValue as String, unit: "")
                state.energyValue = newValue
                BigDecimal costDecimal = newValue * ( kWhCost as BigDecimal)
                def costDisplay = String.format("%5.2f",costDecimal)
                sendEvent(name: "energyTwo", value: "Cost\n\$${costDisplay}", unit: "")
                [name: "energy", value: newValue, unit: "kWh"]
    }
    else if (cmd.scale == 1) {
        [name: "energy", value: cmd.scaledMeterValue, unit: "kVAh"]
    }
    else {
        [name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W"]
    }
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
    [
        name: "switch", value: cmd.value ? "on" : "off", type: "physical"
    ]
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd)
{
    [
        name: "switch", value: cmd.value ? "on" : "off", type: "digital"
    ]
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    // Handles all Z-Wave commands we aren't interested in
    [:]
}

def on() {
    delayBetween([
            zwave.basicV1.basicSet(value: 0xFF).format(),
            zwave.switchBinaryV1.switchBinaryGet().format()
    ])
}

def off() {
    delayBetween([
            zwave.basicV1.basicSet(value: 0x00).format(),
            zwave.switchBinaryV1.switchBinaryGet().format()
    ])
}

def poll() {
    delayBetween([
        zwave.switchBinaryV1.switchBinaryGet().format(),
        zwave.meterV2.meterGet(scale: 0).format(),
        zwave.meterV2.meterGet(scale: 2).format()
    ])
}

def refresh() {
    delayBetween([
        zwave.switchBinaryV1.switchBinaryGet().format(),
        zwave.meterV2.meterGet(scale: 0).format(),
        zwave.meterV2.meterGet(scale: 2).format()
    ])
}

def reset() {

    sendEvent(name: "energyCost", value: "Cost\n--", unit: "")
    return [
        zwave.meterV2.meterReset().format(),
        zwave.meterV2.meterGet(scale: 0).format()
    ]
}

def configure() {
    delayBetween([
        zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 4).format(),       // combined power in watts
        zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue:     300).format(), // every 5 min
        zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: 8).format(),   // combined energy in kWh
        zwave.configurationV1.configurationSet(parameterNumber: 112, size: 4, scaledConfigurationValue:     300).format(), // every 5 min
        zwave.configurationV1.configurationSet(parameterNumber: 103, size: 4, scaledConfigurationValue: 0).format(),    // no third report
        zwave.configurationV1.configurationSet(parameterNumber: 113, size: 4, scaledConfigurationValue: 300).format() // every 5 min
    ])
}
