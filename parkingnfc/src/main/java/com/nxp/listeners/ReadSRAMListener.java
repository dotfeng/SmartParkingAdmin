package com.nxp.listeners;

public interface ReadSRAMListener {
	/**
	 * It informs the listener about new data written in the SRAM
	 * Used to inform about the progress during the SpeedTest
	 * 
	 * @param bytes
	 */
    public abstract void onReadSRAM(byte[] dataRead);
}
