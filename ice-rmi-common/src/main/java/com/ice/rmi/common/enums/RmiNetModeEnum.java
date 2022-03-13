package com.ice.rmi.common.enums;

/**
 * client and server communicate mode
 *
 * @author zjn
 */
public enum RmiNetModeEnum {
    //used in server can connect client
    TWO_WAY,
    //used in server can not connect client on complex network
    ONE_WAY
}
