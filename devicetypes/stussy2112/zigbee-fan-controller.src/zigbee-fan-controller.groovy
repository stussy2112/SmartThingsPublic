/**
 *	Copyright 2019 Sean Williams
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 */

import physicalgraph.zigbee.zcl.DataType

metadata {
	definition (name: "ZigBee Fan Controller", namespace: "stussy2112", author: "Sean Williams", ocfDeviceType: "oic.d.fan", runLocally: true, executeCommandsLocally: true, genericHandler: "Zigbee") {

		capability "Actuator"
		capability "Configuration"
		capability "Fan Speed"
		capability "Refresh"
		capability "Switch"
		capability "Switch Level"
		capability "Health Check"

    command "fanOff"
    command "fanOn"
    command "setFanSpeed"
    command "raiseFanSpeed"
		command "lowerFanSpeed"
    command "lightOn"
    command "lightOff"

	  fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0008, 0202", outClusters: "0003, 0019", manufacturer: "King Of Fans, Inc.", model: "HDC52EastwindFan", deviceJoinName: "Zigbee Fan Controller"
	}

	simulator {
		status "on":  "command: 2003, payload: FF"
		status "off": "command: 2003, payload: 00"
		status "09%": "command: 2003, payload: 09"
		status "10%": "command: 2003, payload: 0A"
		status "33%": "command: 2003, payload: 21"
		status "66%": "command: 2003, payload: 42"
		status "99%": "command: 2003, payload: 63"

		// reply messages
		reply "2001FF,delay 5000,2602": "command: 2603, payload: FF"
		reply "200100,delay 5000,2602": "command: 2603, payload: 00"
		reply "200119,delay 5000,2602": "command: 2603, payload: 19"
		reply "200132,delay 5000,2602": "command: 2603, payload: 32"
		reply "20014B,delay 5000,2602": "command: 2603, payload: 4B"
		reply "200163,delay 5000,2602": "command: 2603, payload: 63"
	}

	tiles(scale: 2) {
		multiAttributeTile(name: "fanSpeed", type: "generic", width: 3, height: 2, canChangeIcon: true) {
			tileAttribute("device.fanSpeed", key: "PRIMARY_CONTROL") {
				attributeState "0", label: "Fan Off", action: "fanOn", icon: "st.thermostat.fan-off", backgroundColor: "#ffffff", nextState: "adjusting"
				attributeState "1", label: "low", action: "fanOff", icon: "st.thermostat.fan-on", backgroundColor: "#00a0dc", nextState: "turningOff"
				attributeState "2", label: "medium", action: "fanOff", icon: "st.thermostat.fan-on", backgroundColor: "#00a0dc", nextState: "turningOff"
				attributeState "3", label: "high", action: "fanOff", icon: "st.thermostat.fan-on", backgroundColor: "#00a0dc", nextState: "turningOff"
				attributeState "4", label: "max", action: "fanOff", icon: "st.thermostat.fan-on", backgroundColor: "#00a0dc", nextState: "turningOff"
				attributeState "adjusting", label: "Adjusting Fan", action:"fanOff", icon:"st.switches.switch.on", backgroundColor:"#00a0dc"
				attributeState "turningOff", label:"Turning Fan Off", action:"fanOn", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"adjusting"
			}
			tileAttribute("device.fanSpeed", key: "VALUE_CONTROL") {
				attributeState "VALUE_UP", action: "raiseFanSpeed"
				attributeState "VALUE_DOWN", action: "lowerFanSpeed"
			}
		}
		multiAttributeTile(name:"light", type: "lighting", width: 3, height: 2, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"off", icon:"st.switches.light.on", backgroundColor:"#00a0dc", nextState:"turningOff"
				attributeState "off", label:'${name}', action:"on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
				attributeState "turningOn", label:"Turning on light", action:"off", icon:"st.switches.light.on", backgroundColor:"#00a0dc", nextState:"on"
				attributeState "turningOff", label:"Turning off light", action:"on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"off"
			}
			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", action:"switch level.setLevel"
			}
		}

		main "fanSpeed"
		details(["fanSpeed", "light"])
	}
}

// Globals
private getFAN_CLUSTER_ID() { 0x0202 }
private getFAN_ATTR_ID() { 0x0000 }

def configure() {
	log.info "Configuring Reporting and Bindings."
	// Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
	// enrolls with default periodic reporting until newer 5 min interval is confirmed
	sendEvent(name: "checkInterval", value: 2 * 10 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])

    // OnOff minReportTime 0 seconds, maxReportTime 5 min. Reporting interval if no activity
    def cmds = [
        // Configure reporting for the ON/OFF switch
        zigbee.onOffConfig(0, 300),
       	// Configure reporting for the Dimmer
        zigbee.levelConfig(0, 300),

        // Configure reporting for the fan (0x0202)
        zigbee.configureReporting(FAN_CLUSTER_ID, FAN_ATTR_ID, DataType.ENUM8, 0, 300, null),
	  	//Get current values from the device
        refresh()
    ]

    return cmds
}

// Parse incoming device messages to generate events
def parse(String description) {
	log.debug "Parsing: ${description}"
    def event = zigbee.getEvent(description)

    if (event) {
        log.info "Device event detected on controller"
        return event
    }

    // Check if the command is for the fan
    def descMap = zigbee.parseDescriptionAsMap(description)
    log.debug "Parsed map ${descMap}"
    if (descMap && descMap.clusterInt == FAN_CLUSTER_ID) {
    	if (descMap.data && descMap.data[0] == "00") {
            log.debug "FAN REPORTING CONFIG RESPONSE: ${zigbee.parse(description)}"
            event = createEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
            sendEvent(event)
            return event
        }
        if (descMap.attrInt == FAN_ATTR_ID) {
            log.info "Fan event detected on controller"
            return createEvent(name: "fanSpeed", value: Math.max(Integer.parseInt(descMap.value), 0))
        }
    }

    log.warn "DID NOT PARSE MESSAGE for description : $description"
	log.debug "parsedDescriptionAsMap: ${descMap}"
}

def off() {
    log.info("Turning Off Light")
    sendEvent(name: "switch", value: "off")
	return zigbee.off()
}

def on() {
    log.info "Turning On Light"
    sendEvent(name: "switch", value: "on")
	return zigbee.on()
}

def fanOff() {
	log.info "Turning Fan Off";
    return setFanSpeed(0)
}

def fanOn() {
	log.info "Turning Fan On";
    return setFanSpeed(state.lastFanSpeed)
}

def setFanSpeed(speed) {
	log.debug "Requested speed is ${speed}"

    def fanNow = device.currentValue("fanSpeed") ?: 0    //save fanspeed before turning off so it can be resumed when turned back on
    log.debug "Current Fan speed is ${fanNow}"

    // only update if the new fan speed is different than the current fan speed
    if (speed != fanNow) {
        state.lastFanSpeed = fanNow
    	def setSpeed = Math.max(Math.min(speed?.intValue() ?: 0, 4), 0)
	    def newFanSpeed = String.format("%02d", setSpeed)		// Map the speed int to the string value. String value is "0" + speed int
    	log.info "Adjusting Fan Speed to " + getFanName()[newFanSpeed]
        sendEvent(name: "fanSpeed", value: setSpeed)
    	return zigbee.writeAttribute(FAN_CLUSTER_ID, FAN_ATTR_ID, DataType.ENUM8, newFanSpeed)
    }
}

def setLevel(value, rate = null) {
	log.debug "value is ${value}"

    def level = Math.max(Math.min(value?.intValue() ?: 0, 100), 0)
    log.info "Adjusting Light Brightness: ${level}"
    if (0 < level) {
    	sendEvent(name: "switch", value: "on")
    } else {
    	sendEvent(name: "switch", value: "off")
    }
    sendEvent(name: "level", value: level)
    return zigbee.setLevel(level)
}

def raiseFanSpeed() {
	def currentValue = device.currentValue("fanSpeed")?.intValue() ?: 0
	return setFanSpeed(Math.min(currentValue + 1, 4))
}

def lowerFanSpeed() {
	def currentValue = device.currentValue("fanSpeed")?.intValue() ?: 0
	return setFanSpeed(Math.max(currentValue - 1, 0))
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	log.info "Pinged..."
	return zigbee.onOffRefresh()
}

def refresh() {
	log.info "Refreshing..."
    return zigbee.onOffRefresh() + zigbee.levelRefresh() + zigbee.readAttribute(FAN_CLUSTER_ID, FAN_ATTR_ID)
}

def installed() {
	if ((device.currentState("level")?.value == null) || (device.currentState("level")?.value == 0)) {
        sendEvent(name: "level", value: 100)
    }
}

private getFanModeName() {
	[
    	0:"Off",
	    1:"Low",
    	2:"Med",
    	3:"High",
  		4:"Max",
    	5:"Off",
    	6:"Comfort Breeze™",
    	7:"Light"
    ]
}

private getFanName() {
	[
    	"00":"Off",
	    "01":"Low",
    	"02":"Med",
    	"03":"High",
  		"04":"Max",
    	"05":"Off",
    	"06":"Comfort Breeze™",
    	"07":"Light"
	]
}