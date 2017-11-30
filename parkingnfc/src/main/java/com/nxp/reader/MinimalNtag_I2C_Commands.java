/*
****************************************************************************
* Copyright(c) 2014 NXP Semiconductors                                     *
* All rights are reserved.                                                 *
*                                                                          *
* Software that is described herein is for illustrative purposes only.     *
* This software is supplied "AS IS" without any warranties of any kind,    *
* and NXP Semiconductors disclaims any and all warranties, express or      *
* implied, including all implied warranties of merchantability,            *
* fitness for a particular purpose and non-infringement of intellectual    *
* property rights.  NXP Semiconductors assumes no responsibility           *
* or liability for the use of the software, conveys no license or          *
* rights under any patent, copyright, mask work right, or any other        *
* intellectual property rights in or to any products. NXP Semiconductors   *
* reserves the right to make changes in the software without notification. *
* NXP Semiconductors also makes no representation or warranty that such    *
* application will be suitable for the specified use without further       *
* testing or modification.                                                 *
*                                                                          *
* Permission to use, copy, modify, and distribute this software and its    *
* documentation is hereby granted, under NXP Semiconductors' relevant      *
* copyrights in the software, without fee, provided that it is used in     *
* conjunction with NXP Semiconductor products(UCODE I2C, NTAG I2C).        *
* This  copyright, permission, and disclaimer notice must appear in all    *
* copies of this code.                                                     *
****************************************************************************
*/
package com.nxp.reader;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.util.Log;

import com.nxp.exceptions.CC_differException;
import com.nxp.exceptions.CommandNotSupportedException;
import com.nxp.exceptions.DynamicLockBitsException;
import com.nxp.exceptions.NotPlusTagException;
import com.nxp.exceptions.StaticLockBitsException;
import com.nxp.listeners.ReadSRAMListener;
import com.nxp.listeners.WriteEEPROMListener;
import com.nxp.listeners.WriteSRAMListener;
import com.nxp.reader.Ntag_Get_Version.Prod;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * Class specific for the functions of The NTAG I2C
 * 
 * @author NXP67729
 * 
 */
public class MinimalNtag_I2C_Commands extends I2C_Enabled_Commands {

	static private final int FirstSectorMemsize = (0xFF - 0x4) * 4;
	private MifareUltralight mfu;
	private Prod tag_type;
	private byte[] answer;
	private static int wait_time = 20;
	private static final String LOG_TAG = "Ntag_I2C_Commands";

	public enum AuthStatus {
		Disabled(0), Unprotected(1), Authenticated(2), Protected_W(3), Protected_RW(4), Protected_W_SRAM(5), Protected_RW_SRAM(6);

		int status;

		private AuthStatus(int status) {
			this.status = status;
		}

		public int getValue() {
			return status;
		}
	}
	
	/**
	 * Special Registers of the NTAG I2C
	 * 
	 */
	public enum Register {
		Session((byte) 0xF8), Configuration((byte) 0xE8), SRAM_Begin(
				(byte) 0xF0), User_memory_Begin((byte) 0x04), UID((byte) 0x00);

		byte value;

		private Register(byte value) {
			this.value = value;
		}

		public byte getValue() {
			return value;
		}
	}

	// ---------------------------------------------------------------------------------
	// Begin Public Functions
	// ---------------------------------------------------------------------------------

	/**
	 * Constructor
	 * 
	 * @param tag
	 *            Tag to connect
	 * @throws IOException
	 */
	public MinimalNtag_I2C_Commands(Tag tag, Prod prod) throws IOException {
		tag_type = prod;
		BlockSize = 4;
		SRAMSize = 64;
		this.mfu = MifareUltralight.get(tag);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#close()
	 */
	@Override
	public void close() throws IOException {
		mfu.close();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#connect()
	 */
	@Override
	public void connect() throws IOException {
		mfu.connect();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#isConnected()
	 */
	@Override
	public boolean isConnected() {
		return mfu.isConnected();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#getLastAnswer()
	 */
	@Override
	public byte[] getLastAnswer() {
		return answer;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#getProduct()
	 */
	@Override
	public Prod getProduct() throws IOException {
		// returns generic NTAG_I2C_1k, because getVersion is not possible
		return tag_type;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#getSessionRegisters()
	 */
	@Override
	public byte[] getSessionRegisters() throws IOException, FormatException,
			CommandNotSupportedException {
		if (tag_type == Prod.NTAG_I2C_1k || tag_type == Prod.NTAG_I2C_2k)
			throw new CommandNotSupportedException(
					"getSessionRegisters not supported");
		
		answer = mfu.readPages(0xEC);
		return answer;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#getConfigRegisters()
	 */
	@Override
	public byte[] getConfigRegisters() throws IOException, FormatException,
			CommandNotSupportedException {
		if (tag_type == Prod.NTAG_I2C_2k)
			throw new CommandNotSupportedException(
					"getConfigRegisters is not Supported for this Phone with NTAG I2C 2k");

		answer = mfu.readPages(0xE8);
		return answer;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.nxp.reader.I2C_Enabled_Commands#getConfigRegister(com.nxp
	 * .reader.Ntag_I2C_Commands.CR_Offset)
	 */
	@Override
	public byte getConfigRegister(CR_Offset off) throws IOException,
            FormatException, CommandNotSupportedException {
		if (tag_type == Prod.NTAG_I2C_2k)
			throw new CommandNotSupportedException(
					"getConfigRegister is not Supported for this Phone with NTAG I2C 2k");

		return getConfigRegisters()[off.getValue()];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.nxp.reader.I2C_Enabled_Commands#getSessionRegister(com.nxp
	 * .reader.Ntag_I2C_Commands.SR_Offset)
	 */
	@Override
	public byte getSessionRegister(SR_Offset off) throws IOException,
            FormatException, CommandNotSupportedException {
		if (tag_type == Prod.NTAG_I2C_1k || tag_type == Prod.NTAG_I2C_2k)
			throw new CommandNotSupportedException(
					"getSessionRegister not supported");
		
		return getSessionRegisters()[off.getValue()];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.nxp.reader.I2C_Enabled_Commands#writeConfigRegisters(byte,
	 * byte, byte, byte, byte, byte)
	 */
	@Override
	public void writeConfigRegisters(byte NC_R, byte LD_R, byte SM_R,
			byte WD_LS_R, byte WD_MS_R, byte I2C_CLOCK_STR) throws IOException,
            FormatException, CommandNotSupportedException {
		if (tag_type == Prod.NTAG_I2C_2k)
			throw new CommandNotSupportedException(
					"writeConfigRegisters is not Supported for this Phone with NTAG I2C 2k");

		byte[] Data = new byte[4];

		// Write the Config Regs
		Data[0] = NC_R;
		Data[1] = LD_R;
		Data[2] = SM_R;
		Data[3] = WD_LS_R;
		mfu.writePage(0xE8, Data);

		Data[0] = WD_MS_R;
		Data[1] = I2C_CLOCK_STR;
		Data[2] = (byte) 0x00;
		Data[3] = (byte) 0x00;
		mfu.writePage(0xE9, Data);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#waitforI2Cwrite()
	 */
	@Override
	public void waitforI2Cwrite(int timeoutMS) throws IOException,
            FormatException {
		// just wait a little

		try {
			Thread.sleep(wait_time);
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "Unexpected error", e);
		}

		return;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#waitforI2Cread()
	 */
	@Override
	public void waitforI2Cread(int timeoutMS) throws IOException,
            FormatException {
		// just wait a little
		try {
			Thread.sleep(wait_time);
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "Unexpected error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#writeEEPROM(byte[])
	 */
	@Override
	public void writeEEPROM(byte[] data, WriteEEPROMListener listener) throws IOException, FormatException,
			CommandNotSupportedException {
		if ((tag_type == Prod.NTAG_I2C_2k || tag_type == Prod.NTAG_I2C_2k_Plus) 
				&& data.length > FirstSectorMemsize) {		
			throw new CommandNotSupportedException(
					"writeEEPROM is not Supported for this Phone, with Data bigger then First Sector("
							+ FirstSectorMemsize + " Bytes)");
		}
		
		if (data.length > getProduct().getMemsize()) {
			throw new IOException("Data is too long");
		}

		byte[] temp;
		int BlockNr = Register.User_memory_Begin.getValue();

		// write till all Data is written
		for (int i = 0; i < data.length; i += 4) {
			temp = Arrays.copyOfRange(data, i, i + 4);
			mfu.writePage(BlockNr, temp);
			BlockNr++;
			
			// Inform the listener about the writing
			if(listener != null)
				listener.onWriteEEPROM(i + 4);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#writeEEPROM(int,
	 * byte[])
	 */
	@Override
	public void writeEEPROM(int startAddr, byte[] data) throws IOException,
            FormatException {
		// Nothing will be done for now
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#readEEPROM(int, int)
	 */
	@Override
	public byte[] readEEPROM(int absStart, int absEnd) throws IOException,
            FormatException, CommandNotSupportedException {

		if ((tag_type == Prod.NTAG_I2C_2k && absEnd > 0xFF)
				|| tag_type == Prod.NTAG_I2C_2k_Plus && absEnd > 0xE1)
			throw new CommandNotSupportedException(
					"readEEPROM is not Supported for this Phone on Second Sector");

		byte[] temp = new byte[0];
		answer = new byte[0];

		if (absStart > 0xFF)
			absStart = 0xFF;

		if (absEnd > 0xFF)
			absEnd = 0xFF;

		int i;
		for (i = absStart; i <= (absEnd - 3); i += 4) {
			temp = mfu.readPages(i);
			answer = concat(answer, temp);
		}

		if (i < absEnd) {
			temp = mfu.readPages(absEnd - 3);
			byte[] bla = Arrays.copyOfRange(temp, (i - (absEnd - 3)) * 4, 16);
			answer = concat(answer, bla);
		}
		return answer;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#writeSRAMBlock(byte[])
	 */
	@Override
	public void writeSRAMBlock(byte[] data, WriteSRAMListener listener) throws IOException,
            FormatException, CommandNotSupportedException {
		
		if (tag_type == Prod.NTAG_I2C_2k)
			throw new CommandNotSupportedException(
					"writeSRAMBlock is not Supported for this Phone with NTAG I2C 2k");

		byte[] TxBuffer = new byte[4];
		int index = 0;

		for (int i = 0; i < 16; i++) {
			for (int d_i = 0; d_i < 4; d_i++) {
				if (index < data.length)
					TxBuffer[d_i] = data[index++];
				else
					TxBuffer[d_i] = (byte) 0x00;
			}
			mfu.writePage(0xF0 + i, TxBuffer);

		}
		
		// Inform the listener about the writing
		if(listener != null)
			listener.onWriteSRAM();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#writeSRAM(byte[],
	 * com.nxp.reader.Ntag_I2C_Commands.R_W_Methods)
	 * 
	 * @throws InterruptedException
	 */
	@Override
	public void writeSRAM(byte[] data, R_W_Methods method, WriteSRAMListener listener) throws IOException,
            FormatException, CommandNotSupportedException {
		
		if (tag_type == Prod.NTAG_I2C_2k)
			throw new CommandNotSupportedException(
					"writeSRAM is not Supported for this Phone with NTAG I2C 2k");

		int Blocks = (int) Math.ceil(data.length / 64.0);
		for (int i = 0; i < Blocks; i++) {

			writeSRAMBlock(data, listener);
			if (method == R_W_Methods.Polling_Mode) {
				waitforI2Cread(100);
			} else {
				try {
					// else wait
					Thread.sleep(6);
				} catch (Exception e) {
					Log.e(LOG_TAG, "Unexpected error", e);
				}
			}

			if (data.length > 64)
				data = Arrays.copyOfRange(data, 64, data.length);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#readSRAMBlock()
	 */
	@Override
	public byte[] readSRAMBlock(ReadSRAMListener listener) throws IOException, FormatException, CommandNotSupportedException {
		if (tag_type == Prod.NTAG_I2C_2k)
			throw new CommandNotSupportedException(
					"readSRAMBlock is not Supported for this Phone with NTAG I2C 2k");
		
		answer = new byte[0];
		for (int i = 0; i < 0x0F; i += 4)
			answer = concat(answer, mfu.readPages(0xF0 + i));

		return answer;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#readSRAM(int,
	 * com.nxp.reader.Ntag_I2C_Commands.R_W_Methods)
	 */
	@Override
	public byte[] readSRAM(int blocks, R_W_Methods method) throws IOException,
            FormatException, CommandNotSupportedException {
		if (tag_type == Prod.NTAG_I2C_2k)
			throw new CommandNotSupportedException(
					"readSRAM is not Supported for this Phone with NTAG I2C 2k");
		
		byte[] response = new byte[0];
		byte[] temp;
		answer = new byte[0];

		for (int i = 0; i < blocks; i++) {
			if (method == R_W_Methods.Polling_Mode) {
				waitforI2Cwrite(100);
			} else {
				try {
					// else wait
					Thread.sleep(6);
				} catch (Exception e) {
					Log.e(LOG_TAG, "Unexpected error", e);
				}
			}
			temp = readSRAMBlock(null);

			// concat read block to the full response
			response = concat(response, temp);
		}
		answer = response;
		return response;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#writeEmptyNdef()
	 */
	@Override
	public void writeEmptyNdef() throws IOException, FormatException {
		// Nothing done for now
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#writeDefaultNdef()
	 */
	@Override
	public void writeDefaultNdef() throws IOException, FormatException {
		byte[] Data = new byte[4];

		Data[0] = (byte) 0x03;
		Data[1] = (byte) 0x60;
		Data[2] = (byte) 0x91;
		Data[3] = (byte) 0x02;
		
		mfu.writePage((byte) 0x04, Data);
		
		Data[0] = (byte) 0x35;
		Data[1] = (byte) 0x53;
		Data[2] = (byte) 0x70;
		Data[3] = (byte) 0x91;
		
		mfu.writePage((byte) 0x05, Data);
		
		Data[0] = (byte) 0x01;
		Data[1] = (byte) 0x14;
		Data[2] = (byte) 0x54;
		Data[3] = (byte) 0x02;
		
		mfu.writePage((byte) 0x06, Data);
		
		Data[0] = (byte) 0x65;
		Data[1] = (byte) 0x6E;
		Data[2] = (byte) 0x4E;
		Data[3] = (byte) 0x54;
		
		mfu.writePage((byte) 0x07, Data);
		
		Data[0] = (byte) 0x41;
		Data[1] = (byte) 0x47;
		Data[2] = (byte) 0x20;
		Data[3] = (byte) 0x49;
		
		mfu.writePage((byte) 0x08, Data);

		Data[0] = (byte) 0x32;
		Data[1] = (byte) 0x43;
		Data[2] = (byte) 0x20;
		Data[3] = (byte) 0x45;
		
		mfu.writePage((byte) 0x09, Data);

		Data[0] = (byte) 0x58;
		Data[1] = (byte) 0x50;
		Data[2] = (byte) 0x4C;
		Data[3] = (byte) 0x4F;
		
		mfu.writePage((byte) 0x0A, Data);
		
		Data[0] = (byte) 0x52;
		Data[1] = (byte) 0x45;
		Data[2] = (byte) 0x52;
		Data[3] = (byte) 0x51;
		
		mfu.writePage((byte) 0x0B, Data);
		
		Data[0] = (byte) 0x01;
		Data[1] = (byte) 0x19;
		Data[2] = (byte) 0x55;
		Data[3] = (byte) 0x01;
		
		mfu.writePage((byte) 0x0C, Data);
		
		Data[0] = (byte) 0x6E;
		Data[1] = (byte) 0x78;
		Data[2] = (byte) 0x70;
		Data[3] = (byte) 0x2E;
		
		mfu.writePage((byte) 0x0D, Data);
		
		Data[0] = (byte) 0x63;
		Data[1] = (byte) 0x6F;
		Data[2] = (byte) 0x6D;
		Data[3] = (byte) 0x2F;
		
		mfu.writePage((byte) 0x0E, Data);

		Data[0] = (byte) 0x64;
		Data[1] = (byte) 0x65;
		Data[2] = (byte) 0x6D;
		Data[3] = (byte) 0x6F;
		
		mfu.writePage((byte) 0x0F, Data);
		
		Data[0] = (byte) 0x62;
		Data[1] = (byte) 0x6F;
		Data[2] = (byte) 0x61;
		Data[3] = (byte) 0x72;
		
		mfu.writePage((byte) 0x10, Data);
		
		Data[0] = (byte) 0x64;
		Data[1] = (byte) 0x2F;
		Data[2] = (byte) 0x4F;
		Data[3] = (byte) 0x4D;
		
		mfu.writePage((byte) 0x11, Data);
		
		Data[0] = (byte) 0x35;
		Data[1] = (byte) 0x35;
		Data[2] = (byte) 0x36;
		Data[3] = (byte) 0x39;
		
		mfu.writePage((byte) 0x12, Data);
		
		Data[0] = (byte) 0x54;
		Data[1] = (byte) 0x0F;
		Data[2] = (byte) 0x14;
		Data[3] = (byte) 0x61;
		
		mfu.writePage((byte) 0x13, Data);
		
		Data[0] = (byte) 0x6E;
		Data[1] = (byte) 0x64;
		Data[2] = (byte) 0x72;
		Data[3] = (byte) 0x6F;
		
		mfu.writePage((byte) 0x14, Data);

		Data[0] = (byte) 0x69;
		Data[1] = (byte) 0x64;
		Data[2] = (byte) 0x2E;
		Data[3] = (byte) 0x63;
		
		mfu.writePage((byte) 0x15, Data);
		
		Data[0] = (byte) 0x6F;
		Data[1] = (byte) 0x6D;
		Data[2] = (byte) 0x3A;
		Data[3]= (byte) 0x70;
		
		mfu.writePage((byte) 0x16, Data);

		Data[0] = (byte) 0x6B;
		Data[1] = (byte) 0x67;
		Data[2] = (byte) 0x63;
		Data[3] = (byte) 0x6F;
		
		mfu.writePage((byte) 0x17, Data);
		
		Data[0] = (byte) 0x6D;
		Data[1] = (byte) 0x2E;
		Data[2] = (byte) 0x6E;
		Data[3] = (byte) 0x78;
		
		mfu.writePage((byte) 0x18, Data);

		Data[0] = (byte) 0x70;
		Data[1] = (byte) 0x2E;
		Data[2] = (byte) 0x6E;
		Data[3] = (byte) 0x74;
		
		mfu.writePage((byte) 0x19, Data);
		
		Data[0] = (byte) 0x61;
		Data[1] = (byte) 0x67;
		Data[2] = (byte) 0x69;
		Data[3] = (byte) 0x32;
		
		mfu.writePage((byte) 0x1A, Data);
		
		Data[0] = (byte) 0x63;
		Data[1] = (byte) 0x64;
		Data[2] = (byte) 0x65;
		Data[3] = (byte) 0x6D;
		
		mfu.writePage((byte) 0x1B, Data);
		
		Data[0] = (byte) 0x6F;
		Data[1] = (byte) 0x5F;
		Data[2] = (byte) 0xFE;
		Data[3] = (byte) 0x00;
		
		mfu.writePage((byte) 0x1C, Data);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#writeDeliveryNdef()
	 */
	@Override
	public int writeDeliveryNdef() throws IOException, FormatException,
			CC_differException, StaticLockBitsException,
			DynamicLockBitsException {
		// Nothing done for now
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#writeNDEF()
	 */
	@Override
	public void writeNDEF(NdefMessage message, WriteEEPROMListener listener) throws IOException,
            FormatException, CommandNotSupportedException {
		byte[] Ndef_message_byte = createRawNdefTlv(message);
		writeEEPROM(Ndef_message_byte, listener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#readNDEF()
	 */
	@Override
	public NdefMessage readNDEF() throws IOException, FormatException, CommandNotSupportedException {
		int NDEFsize;
		int TLVsize;
		int TLV_plus_NDEF;

		// get TLV
		byte[] TLV = readEEPROM(Register.User_memory_Begin.getValue(),
				Register.User_memory_Begin.getValue() + 3);

		// checking TLV - maybe there are other TLVs on the tag
		if (TLV[0] != 0x03) {
			throw new FormatException("Format on Tag not supported");
		}

		if (TLV[1] != (byte) 0xFF) {
			NDEFsize = (TLV[1] & 0xFF);
			TLVsize = 2;
			TLV_plus_NDEF = TLVsize + NDEFsize;
		} else {
			NDEFsize = (TLV[3] & 0xFF);
			NDEFsize |= ((TLV[2] << 8) & 0xFF00);
			TLVsize = 4;
			TLV_plus_NDEF = TLVsize + NDEFsize;
		}

		// Read NDEF Message
		byte[] data = readEEPROM(Register.User_memory_Begin.getValue(),
				Register.User_memory_Begin.getValue() + (TLV_plus_NDEF / 4));

		// delete TLV
		data = Arrays.copyOfRange(data, TLVsize, data.length);
		// delete end of String which is not part of the NDEF Message
		data = Arrays.copyOf(data, NDEFsize);

		// Interpret Bytes
		NdefMessage message = new NdefMessage(data);
		return message;
	}

	// -------------------------------------------------------------------
	// Helping function
	// -------------------------------------------------------------------

	/**
	 * create a Raw NDEF TLV from a NDEF Message
	 * 
	 * @param NDEFmessage
	 *            NDEF Message to put in the NDEF TLV
	 * @return Byte Array of NDEF Message
	 * @throws UnsupportedEncodingException
	 */
	private byte[] createRawNdefTlv(NdefMessage NDEFmessage)
			throws UnsupportedEncodingException {
		// creating NDEF
		byte[] Ndef_message_byte = NDEFmessage.toByteArray();
		int ndef_message_size = Ndef_message_byte.length;
		byte[] message;

		if (ndef_message_size < 0xFF) {
			message = new byte[ndef_message_size + 3];
			byte TLV_size = 0;
			TLV_size = (byte) ndef_message_size;
			message[0] = (byte) 0x03;
			message[1] = (byte) TLV_size;
			message[message.length - 1] = (byte) 0xFE;
			System.arraycopy(Ndef_message_byte, 0, message, 2,
					Ndef_message_byte.length);
		} else {
			message = new byte[ndef_message_size + 5];
			int TLV_size = ndef_message_size;
			TLV_size |= 0xFF0000;
			message[0] = (byte) 0x03;
			message[1] = (byte) ((TLV_size >> 16) & 0xFF);
			message[2] = (byte) ((TLV_size >> 8) & 0xFF);
			message[3] = (byte) (TLV_size & 0xFF);
			message[message.length - 1] = (byte) 0xFE;
			System.arraycopy(Ndef_message_byte, 0, message, 4,
					Ndef_message_byte.length);
		}

		return message;
	}

	@Override
	public Boolean checkPTwritePossible() throws IOException, FormatException {
		// Just wait some time
		try {
			Thread.sleep(wait_time);
		} catch (Exception e) {
			Log.e(LOG_TAG, "Unexpected error", e);
		}

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#authenticatePlus()
	 * 
	 */
	@Override
	public byte[] authenticatePlus(byte[] pwd) throws IOException, NotPlusTagException {
		if(getProduct() != Prod.NTAG_I2C_1k_Plus && getProduct() != Prod.NTAG_I2C_2k_Plus) {
			throw new NotPlusTagException(
					"Auth Operations are not supported by non NTAG I2C PLUS products");
		} 
		
		byte[] command = new byte[5];
		command[0] = (byte) 0x1B;
		command[1] = pwd[0];
		command[2] = pwd[1];
		command[3] = pwd[2];
		command[4] = pwd[3];
		return mfu.transceive(command);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#protectPlus()
	 * 
	 */
	@Override
	public void protectPlus(byte[] pwd, byte startAddr)
			throws IOException, FormatException, NotPlusTagException  {
		byte[] Data = new byte[4];
		
		if(getProduct() != Prod.NTAG_I2C_1k_Plus && getProduct() != Prod.NTAG_I2C_2k_Plus) {
			throw new NotPlusTagException(
					"Auth Operations are not supported by non NTAG I2C PLUS products");
		} 
		
		// Set the password indicated by the user
		mfu.writePage(0xE5, pwd);
		
		byte access = (byte) 0x00;
		byte auth_lim = 0x00; 										// Don't limit the number of auth attempts
		
		access ^= 1 << Access_Offset.NFC_PROT.getValue();			// NFC_Prot
		access ^= 0 << Access_Offset.NFC_DIS_SEC1.getValue();		// NFC_DIS_SEC1
		access |= auth_lim << Access_Offset.AUTH_LIM.getValue();	// AUTHLIM 		
		
		// Write the ACCESS configuration
		Data[0] = access;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = 0x00;
		mfu.writePage(0xE4, Data);
		
		byte pt_i2c = 0x00;
		byte i2c_prot = 0x00; 

		pt_i2c ^= 0 << PT_I2C_Offset.K2_PROT.getValue();			// 2K Prot
		pt_i2c ^= 1 << PT_I2C_Offset.SRAM_PROT.getValue();			// SRAM Prot
		pt_i2c |= i2c_prot << PT_I2C_Offset.I2C_PROT.getValue();	// I2C Prot

		// Write the PT_I2C configuration
		Data[0] = pt_i2c;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = 0x00;
		mfu.writePage(0xE7, Data);
				
		// Write the AUTH0 lock starting page
		Data[0] = 0x00;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = startAddr;
		mfu.writePage(0xE3, Data);
		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#unprotectPlus()
	 * 
	 */
	@Override
	public void unprotectPlus() throws IOException, FormatException, NotPlusTagException  {
		byte[] Data = new byte[4];
		
		if(getProduct() != Prod.NTAG_I2C_1k_Plus && getProduct() != Prod.NTAG_I2C_2k_Plus) {
			throw new NotPlusTagException(
					"Auth Operations are not supported by non NTAG I2C PLUS products");
		} 
		
		Data[0] = 0x00;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = (byte) 0xFF;
		mfu.writePage(0xE3, Data);
		
		// Set the password to FFs
		Data[0] = (byte) 0xFF;
		Data[1] = (byte) 0xFF;
		Data[2] = (byte) 0xFF;
		Data[3] = (byte) 0xFF;
		mfu.writePage(0xE5, Data);
		
		// Write the ACCESS configuration
		Data[0] = 0x00;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = 0x00;
		mfu.writePage(0xE4, Data);
		
		// Write the PT I2C configuration
		Data[0] = 0x00;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = 0x00;
		mfu.writePage(0xE7, Data);
		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#getProtectionPlus()
	 * 
	 */
	@Override
	public int getProtectionPlus() {
		try {		
			byte[] auth0 = getAuth0Register();
			
			if(auth0 != null && auth0.length < 4) {
				try {
					readSRAMBlock(null);
					return AuthStatus.Protected_RW.getValue();
				} catch (IOException e) {
					Log.e(LOG_TAG, "Unexpected error", e);
				} catch (FormatException e) {
					Log.e(LOG_TAG, "Unexpected error", e);
				}
				
				return AuthStatus.Protected_RW_SRAM.getValue();
			} else {
				if((auth0[3] & 0xFF) <= 0xEB) { 
					byte[] access = getAccessRegister();
					byte[] pti2c = getPTI2CRegister();
					
					if (((0x0000080 & access[0]) >> Access_Offset.NFC_PROT.getValue() == 1) &&
							((0x0000004 & pti2c[0]) >> PT_I2C_Offset.SRAM_PROT.getValue() == 1))
						return AuthStatus.Protected_RW_SRAM.getValue();
					else if (((0x0000080 & access[0]) >> Access_Offset.NFC_PROT.getValue() == 1) &&
							((0x0000004 & pti2c[0]) >> PT_I2C_Offset.SRAM_PROT.getValue() == 0))
						return AuthStatus.Protected_RW.getValue();
					else if (((0x0000080 & access[0]) >> Access_Offset.NFC_PROT.getValue() == 0) &&
							((0x0000004 & pti2c[0]) >> PT_I2C_Offset.SRAM_PROT.getValue() == 1))
						return AuthStatus.Protected_W_SRAM.getValue();
					else if (((0x0000080 & access[0]) >> Access_Offset.NFC_PROT.getValue() == 0) &&
							((0x0000004 & pti2c[0]) >> PT_I2C_Offset.SRAM_PROT.getValue() == 0))
						return AuthStatus.Protected_W.getValue();
				}
			}
	
			return AuthStatus.Unprotected.getValue();
		} catch (IOException | FormatException | CommandNotSupportedException e) {
            Log.e(LOG_TAG, "Unexpected error", e);
        }
		
		// Check if the SRAM is lock
		try {
			readSRAMBlock(null);
			return AuthStatus.Protected_RW.getValue();
		} catch (IOException | FormatException | CommandNotSupportedException e) {
			Log.e(LOG_TAG, "Unexpected error", e);
		}
		
		return AuthStatus.Protected_RW_SRAM.getValue();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#getAuth0Register()
	 * 
	 */
	@Override
	public byte[] getAuth0Register() throws IOException, FormatException,
			CommandNotSupportedException {
		return mfu.readPages(0xE3);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#getAccessRegister()
	 * 
	 */
	@Override
	public byte[] getAccessRegister() throws IOException, FormatException,
			CommandNotSupportedException {
		return mfu.readPages(0xE4);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#getPTI2CRegister()
	 * 
	 */
	@Override
	public byte[] getPTI2CRegister() throws IOException, FormatException,
			CommandNotSupportedException {
		return mfu.readPages(0xE7);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#writeAuthRegisters()
	 * 
	 */
	@Override
	public void writeAuthRegisters(byte auth0, byte access, byte pt_i2c) throws IOException, FormatException,
			CommandNotSupportedException {
		byte[] Data = new byte[4];
		
		// Write the ACCESS configuration
		Data[0] = access;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = 0x00;
		mfu.writePage(0xE4, Data);
		
		// Write the PT I2C configuration
		Data[0] = pt_i2c;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = 0x00;
		mfu.writePage(0xE7, Data);
		
		// Set the password to FFs
		Data[0] = (byte) 0xFF;
		Data[1] = (byte) 0xFF;
		Data[2] = (byte) 0xFF;
		Data[3] = (byte) 0xFF;
		mfu.writePage(0xE5, Data);
		
		// Set the pack to 00s
		Data[0] = (byte) 0x00;
		Data[1] = (byte) 0x00;
		Data[2] = (byte) 0x00;
		Data[3] = (byte) 0x00;
		mfu.writePage(0xE6, Data);
		
		Data[0] = 0x00;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = auth0;
		mfu.writePage(0xE3, Data);		
	}
}
