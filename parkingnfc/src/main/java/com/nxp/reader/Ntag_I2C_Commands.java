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

import com.nxp.exceptions.CC_differException;
import com.nxp.exceptions.CommandNotSupportedException;
import com.nxp.exceptions.DynamicLockBitsException;
import com.nxp.exceptions.NotPlusTagException;
import com.nxp.exceptions.StaticLockBitsException;
import com.nxp.listeners.WriteEEPROMListener;
import com.nxp.listeners.WriteSRAMListener;
import com.nxp.reader.Ntag_Get_Version.Prod;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import com.nxp.reader.MinimalNtag_I2C_Commands.AuthStatus;

/**
 * Class specific for the functions of The NTAG I2C
 * 
 * @author NXP67729
 * 
 */
public class Ntag_I2C_Commands extends I2C_Enabled_Commands {
	public static final int DEFAULT_NDEF_MESSAGE_SIZE = 0;
	public static final int EMPTY_NDEF_MESSAGE_SIZE = 104;

	Ntag_Commands reader;
	Tag tag;
	byte[] answer;
	byte[] session_registers;
	Ntag_Get_Version get_version_response;
	byte sram_sector;
	boolean TimeOut = false;
	Object lock = new Object();

	/**
	 * Special Registers of the NTAG I2C
	 * 
	 */
	public enum Register {
		Session((byte) 0xF8), Session_PLUS((byte) 0xEC), Configuration((byte) 0xE8), SRAM_Begin((byte) 0xF0), 
		Capability_Container((byte) 0x03), User_memory_Begin((byte) 0x04), UID((byte) 0x00),
		AUTH0((byte) 0xE3), ACCESS((byte) 0xE4), PWD((byte) 0xE5), PACK((byte) 0xE6), PT_I2C((byte) 0xE7);

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
	public Ntag_I2C_Commands(Tag tag) throws IOException {
		BlockSize = 4;
		SRAMSize = 64;
		this.reader = new Ntag_Commands(tag);
		this.tag = tag;
		connect();
		if (getProduct() == Prod.NTAG_I2C_2k)
			sram_sector = 1;
		else
			sram_sector = 0;
		close();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#close()
	 */
	@Override
	public void close() throws IOException {
		reader.close();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#connect()
	 */
	@Override
	public void connect() throws IOException {
		reader.connect();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#isConnected()
	 */
	@Override
	public boolean isConnected() {
		return reader.isConnected();
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
		if (get_version_response == null) {
			try {
				get_version_response = new Ntag_Get_Version(reader.getVersion());
			} catch (Exception e) {
				e.printStackTrace();
				try {
					reader.close();
					reader.connect();
					byte[] temp = reader.read((byte) 0x00);

					if (temp[0] == (byte) 0x04 && temp[12] == (byte) 0xE1
							&& temp[13] == (byte) 0x10
							&& temp[14] == (byte) 0x6D
							&& temp[15] == (byte) 0x00) {

						temp = reader.read((byte) 0xE8);
						get_version_response = Ntag_Get_Version.NTAG_I2C_1k;

					} else if (temp[0] == (byte) 0x04
							&& temp[12] == (byte) 0xE1
							&& temp[13] == (byte) 0x10
							&& temp[14] == (byte) 0xEA
							&& temp[15] == (byte) 0x00) {
						get_version_response = Ntag_Get_Version.NTAG_I2C_2k;
					}
				} catch (FormatException e2) {
					reader.close();
					reader.connect();
					e2.printStackTrace();
					get_version_response = Ntag_Get_Version.NTAG_I2C_1k;
				}

			}
		}

		return get_version_response.Get_Product();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#getSessionRegisters()
	 */
	@Override
	public byte[] getSessionRegisters() throws IOException, FormatException {
		if (getProduct() == Prod.NTAG_I2C_1k_Plus || getProduct() == Prod.NTAG_I2C_2k_Plus) {
			reader.SectorSelect((byte) 0);
			return reader.read(Register.Session_PLUS.getValue());
		} else {
			reader.SectorSelect((byte) 3);
			return reader.read(Register.Session.getValue());
		}		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#getConfigRegisters()
	 */
	@Override
	public byte[] getConfigRegisters() throws IOException, FormatException {

		if (getProduct() == Prod.NTAG_I2C_1k || getProduct() == Prod.NTAG_I2C_1k_Plus  || getProduct() == Prod.NTAG_I2C_2k_Plus)
			reader.SectorSelect((byte) 0);
		else if (getProduct() == Prod.NTAG_I2C_2k)
			reader.SectorSelect((byte) 1);
		else
			throw new IOException();

		return reader.read(Register.Configuration.getValue());
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
            FormatException {
		byte[] register = getConfigRegisters();
		return register[off.getValue()];
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
            FormatException {
		byte[] register = getSessionRegisters();
		return register[off.getValue()];
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
            FormatException {
		byte[] Data = new byte[4];

		if (getProduct() == Prod.NTAG_I2C_1k || getProduct() == Prod.NTAG_I2C_1k_Plus || getProduct() == Prod.NTAG_I2C_2k_Plus)
			reader.SectorSelect((byte) 0);
		else if (getProduct() == Prod.NTAG_I2C_2k)
			reader.SectorSelect((byte) 1);
		else
			throw new IOException();

		// Write the Config Regs
		Data[0] = NC_R;
		Data[1] = LD_R;
		Data[2] = SM_R;
		Data[3] = WD_LS_R;
		reader.write(Data, Register.Configuration.getValue());

		Data[0] = WD_MS_R;
		Data[1] = I2C_CLOCK_STR;
		Data[2] = (byte) 0x00;
		Data[3] = (byte) 0x00;
		reader.write(Data, (byte) (Register.Configuration.getValue() + 1));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#waitforI2Cwrite()
	 */
	@Override
	public void waitforI2Cwrite(int timeoutMS) throws IOException,
            FormatException, TimeoutException {
//		reader.SectorSelect((byte) 3);
//
//		TimeOut = false;
//
//		// interrupts the wait after timoutMS milliseconds
//		Timer mTimer = new Timer();
//		mTimer.schedule(new TimerTask() {
//			@Override
//			public void run() {
//				synchronized (lock) {
//					TimeOut = true;
//				}
//			}
//		}, timeoutMS);
//
//		// if SRAM_RF_RDY is set the Reader can Read
//		while ((getSessionRegister(SR_Offset.NS_REG) & NS_Reg_Func.SRAM_RF_READY
//				.getValue()) == 0) {
//			synchronized (lock) {
//				if (TimeOut)
//					throw new TimeoutException("waitforI2Cwrite had a Timout");
//			}
//		}
//
//		mTimer.cancel();
//		synchronized (lock) {
//			TimeOut = true;
//		}
		
		try {
			Thread.sleep(timeoutMS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#waitforI2Cread()
	 */
	@Override
	public void waitforI2Cread(int timeoutMS) throws IOException, FormatException, TimeoutException {
//		reader.SectorSelect((byte) 3);
//
//		TimeOut = false;
//
//		// interrupts the wait after timoutMS milliseconds
//		Timer mTimer = new Timer();
//		mTimer.schedule(new TimerTask() {
//			@Override
//			public void run() {
//				synchronized (lock) {
//					TimeOut = true;
//				}
//			}
//		}, timeoutMS);
//
//		// if SRAM_I2C_READY is set the Reader can write
//		while (((getSessionRegister(SR_Offset.NS_REG) & NS_Reg_Func.SRAM_I2C_READY
//				.getValue()) == NS_Reg_Func.SRAM_I2C_READY.getValue())) {
//			if (TimeOut)
//				throw new TimeoutException("waitforI2Cread had a Timout");
//		}
//
//		mTimer.cancel();
//		synchronized (lock) {
//			TimeOut = true;
//		}

		try {
			Thread.sleep(timeoutMS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#writeEEPROM(byte[])
	 */
	@Override
	public void writeEEPROM(byte[] data, WriteEEPROMListener listener) throws IOException, FormatException {
		if (data.length > getProduct().getMemsize()) {
			throw new IOException("Data is to long");
		}

		reader.SectorSelect((byte) 0);
		byte[] temp;
		int Index = 0;
		byte BlockNr = Register.User_memory_Begin.getValue();
		
		// write till all Data is written or the Block 0xFF was written(BlockNr
		// should be 0 then, because of the type byte)
		for (Index = 0; Index < data.length && BlockNr != 0; Index += 4) {
			// NTAG I2C Plus sits the Config registers in Sector 0
			if(getProduct() == Prod.NTAG_I2C_2k_Plus && BlockNr == (byte) 0xE2)
				break;

			temp = Arrays.copyOfRange(data, Index, Index + 4);
			reader.write(temp, BlockNr);
			BlockNr++;
			
			// Inform the listener about the writing
			if(listener != null)
				listener.onWriteEEPROM(Index + 4);
		}

		// If Data is left write to the 1. Sector
		if (Index < data.length) {
			reader.SectorSelect((byte) 1);
			BlockNr = 0;

			for (; Index < data.length; Index += 4) {
				temp = Arrays.copyOfRange(data, Index, Index + 4);
				reader.write(temp, BlockNr);
				BlockNr++;
				
				// Inform the listener about the writing
				if(listener != null)
					listener.onWriteEEPROM(Index + 4);
			}
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

		if ((startAddr & 0x100) != 0x000 && (startAddr & 0x200) != 0x100) {
			throw new FormatException("Sector not supported");
		}

		reader.SectorSelect((byte) ((startAddr & 0x200) >> 16));
		byte[] temp;
		int Index = 0;
		byte BlockNr = (byte) (startAddr & 0xFF);

		// write till all Data is written or the Block 0xFF was written(BlockNr
		// should be
		// 0 then, because of the type byte)
		for (Index = 0; Index < data.length && BlockNr != 0; Index += 4) {
			temp = Arrays.copyOfRange(data, Index, Index + 4);
			reader.write(temp, BlockNr);
			BlockNr++;
		}

		// If Data is left write and the first Sector was not already written
		// switch to the first
		if (Index < data.length && (startAddr & 0x100) != 0x100) {
			reader.SectorSelect((byte) 1);
			BlockNr = 0;
			for (; Index < data.length; Index += 4) {
				temp = Arrays.copyOfRange(data, Index, Index + 4);
				reader.write(temp, BlockNr);
				BlockNr++;
			}
		} else if ((startAddr & 0x100) == 0x100) {
			throw new IOException("Data is to long");
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#readEEPROM(int, int)
	 */
	@Override
	public byte[] readEEPROM(int absStart, int absEnd) throws IOException,
            FormatException {
		int maxfetchsize = reader.getMaxTransceiveLength();
		int max_fast_read = (maxfetchsize - 2) / 4;
		int fetch_start = absStart;
		int fetch_end = 0;
		byte data[] = null;
		byte temp[] = null;

		reader.SectorSelect((byte) 0);

		while (fetch_start <= absEnd) {
			fetch_end = fetch_start + max_fast_read - 1;
			// check for last read, fetch only rest
			if (fetch_end > absEnd)
				fetch_end = absEnd;
			
			// check for sector change in between and reduce fast_read to stay within sector
			if (getProduct() != Prod.NTAG_I2C_2k_Plus) {
				if ((fetch_start & 0xFF00) != (fetch_end & 0xFF00))
					fetch_end = (fetch_start & 0xFF00) + 0xFF;
			} else {
				if ((fetch_start & 0xFF00) == 0 && (fetch_end > 0xE2))
					fetch_end = (fetch_start & 0xFF00) + 0xE1;
			}

			temp = reader.fast_read((byte) (fetch_start & 0x00FF), (byte) (fetch_end & 0x00FF));
			data = concat(data, temp);
			
			// calculate next fetch_start
			fetch_start = fetch_end + 1;

			// check for sector change in between and reduce fast_read to stay within sector
			if (getProduct() != Prod.NTAG_I2C_2k_Plus) {
				if ((fetch_start & 0xFF00) != (fetch_end & 0xFF00))
					reader.SectorSelect((byte) 1);
			} else {
				if ((fetch_start & 0xFF00) == 0 && (fetch_end >= 0xE1)) {
					reader.SectorSelect((byte) 1);
					fetch_start = 0x100;
					
					// Update the absEnd with pages not read on Sector 0
					absEnd = absEnd + (0xFF - 0xE2);
				}
			}
		}
	
		// Let's go back to Sector 0
		reader.SectorSelect((byte) 0);
	
		return data;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#writeSRAMBlock(byte[])
	 */
	@Override
	public void writeSRAMBlock(byte[] data, WriteSRAMListener listener) throws IOException, FormatException {
		byte[] TxBuffer = new byte[4];
		int index = 0;

		reader.SectorSelect(sram_sector);

		/**
		 * Samsung controllers do not like NfcA Transceive method, so it is better using MUL writePage command when possible
		 * For NTAG_I2C_2k it is not possible to use MUL commands because when establishing the connection the Sector is moved back to 0
		 */
		
		if(getProduct() == Prod.NTAG_I2C_1k_Plus || getProduct() == Prod.NTAG_I2C_2k_Plus) {
			reader.fast_write(data, (byte) Register.SRAM_Begin.getValue(), (byte) (Register.SRAM_Begin.getValue() + 0x0F));	
		} else {
			if (getProduct() == Prod.NTAG_I2C_1k) {
				reader.close();

				MifareUltralight ul = MifareUltralight.get(tag);
				if (ul != null) {
					ul.connect();
					
					int SRAM_Begin = (int) Register.SRAM_Begin.getValue() & 0xFF;
					
					for (int i = 0; i < 16; i++) {
						for (int d_i = 0; d_i < 4; d_i++) {
							if (index < data.length)
								TxBuffer[d_i] = data[index++];
							else
								TxBuffer[d_i] = (byte) 0x00;
						}
			
						ul.writePage(SRAM_Begin + i, TxBuffer);
					}
					
					ul.close();
				}
				
				reader.connect();
			} else {
				authenticateIfNeeded();

				for (int i = 0; i < 16; i++) {
					for (int d_i = 0; d_i < 4; d_i++) {
						if (index < data.length)
							TxBuffer[d_i] = data[index++];
						else
							TxBuffer[d_i] = (byte) 0x00;
					}
					
					reader.write(TxBuffer, (byte) (Register.SRAM_Begin.getValue() + i));
				}
			}
		}
			
		// Inform the listener about the writing
		if(listener != null)
			listener.onWriteSRAM();
	}

	private void authenticateIfNeeded() {
		// TODO
		// [Ariel Alvarez]: La implementacion de la demo original contiene problemas
		// porque se encuentra acoplada a la Activity y depende de variables de clase.
		// Para poder extraer la libreria de NFC como modulo tuve que remover esto.
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
		
		return reader.pwdAuth(pwd);
	}
		
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#protectPlus()
	 * 
	 */
	@Override
	public void protectPlus(byte[] pwd, byte startAddr) throws IOException, FormatException, NotPlusTagException {
		byte[] Data = new byte[4];
		
		if(getProduct() != Prod.NTAG_I2C_1k_Plus && getProduct() != Prod.NTAG_I2C_2k_Plus) {
			throw new NotPlusTagException(
					"Auth Operations are not supported by non NTAG I2C PLUS products");
		} 
		
		reader.SectorSelect((byte) 0);
		
		// Set the password indicated by the user
		reader.write(pwd, Register.PWD.getValue());
		
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
		reader.write(Data, Register.ACCESS.getValue());
		
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
		reader.write(Data, Register.PT_I2C.getValue());
				
		// Write the AUTH0 lock starting page
		Data[0] = 0x00;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = startAddr;
		reader.write(Data, Register.AUTH0.getValue());
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#unprotectPlus()
	 * 
	 */
	@Override
	public void unprotectPlus() throws IOException, FormatException, NotPlusTagException {
		byte[] Data = new byte[4];
		
		if(getProduct() != Prod.NTAG_I2C_1k_Plus && getProduct() != Prod.NTAG_I2C_2k_Plus) {
			throw new NotPlusTagException(
					"Auth Operations are not supported by non NTAG I2C PLUS products");
		} 
		
		reader.SectorSelect((byte) 0);
		
		Data[0] = 0x00;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = (byte) 0xFF;
		reader.write(Data, Register.AUTH0.getValue());
		
		// Set the password to FFs
		Data[0] = (byte) 0xFF;
		Data[1] = (byte) 0xFF;
		Data[2] = (byte) 0xFF;
		Data[3] = (byte) 0xFF;
		reader.write(Data, Register.PWD.getValue());
		
		// Write the ACCESS configuration
		Data[0] = 0x00;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = 0x00;
		reader.write(Data, Register.ACCESS.getValue());
		
		// Write the PT I2C configuration
		Data[0] = 0x00;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = 0x00;
		reader.write(Data, Register.PT_I2C.getValue());
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
			reader.SectorSelect((byte) 0);
			byte[] auth0 = getAuth0Register();
			
			if(auth0 != null && auth0.length < 4) {
				try {
					readSRAMBlock();
					return AuthStatus.Protected_RW.getValue();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (FormatException e) {
					e.printStackTrace();
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
		} catch (IOException e) {
			e.printStackTrace();
		} catch (FormatException e) {
			e.printStackTrace();
		} catch (CommandNotSupportedException e) {
			e.printStackTrace();
		}
		
		// Check if the SRAM is lock
		try {
			readSRAMBlock();
			return AuthStatus.Protected_RW.getValue();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (FormatException e) {
			e.printStackTrace();
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
		reader.SectorSelect((byte) 0);
		return reader.read(Register.AUTH0.getValue());
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
		reader.SectorSelect((byte) 0);
		return reader.read(Register.ACCESS.getValue());
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
		reader.SectorSelect((byte) 0);
		return reader.read(Register.PT_I2C.getValue());
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
				
		reader.SectorSelect((byte) 0);

		// Write the ACCESS configuration
		Data[0] = access;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = 0x00;
		reader.write(Data, Register.ACCESS.getValue());
		
		// Write the PT I2C configuration
		Data[0] = pt_i2c;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = 0x00;
		reader.write(Data, Register.PT_I2C.getValue());
		
		// Set the password to FFs
		Data[0] = (byte) 0xFF;
		Data[1] = (byte) 0xFF;
		Data[2] = (byte) 0xFF;
		Data[3] = (byte) 0xFF;
		reader.write(Data, Register.PWD.getValue());
		
		// Set the pack to 00s
		Data[0] = (byte) 0x00;
		Data[1] = (byte) 0x00;
		Data[2] = (byte) 0x00;
		Data[3] = (byte) 0x00;
		reader.write(Data, Register.PACK.getValue());
		
		Data[0] = 0x00;
		Data[1] = 0x00;
		Data[2] = 0x00;
		Data[3] = auth0;
		reader.write(Data, Register.AUTH0.getValue());
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
            FormatException, TimeoutException {

		int Blocks = (int) Math.ceil(data.length / 64.0);
		for (int i = 0; i < Blocks; i++) {
			byte[] dataBlock = new byte[64];
			if (data.length - (i + 1) * 64 < 0) {					
				Arrays.fill(dataBlock, (byte) 0);
				System.arraycopy(data, i * 64, dataBlock, 0, data.length % 64);
			} else {
				System.arraycopy(data, i * 64, dataBlock, 0, 64);
			}
			
			writeSRAMBlock(dataBlock, listener);
			if (method == R_W_Methods.Polling_Mode) {
				waitforI2Cread(100);
			} else {
				try {
					// else wait
					Thread.sleep(6);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#readSRAMBlock()
	 */
	@Override
	public byte[] readSRAMBlock() throws IOException, FormatException {
		answer = new byte[0];
		reader.SectorSelect(sram_sector);
		answer = reader.fast_read((byte) 0xF0, (byte) 0xFF);
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
            FormatException, TimeoutException {
		byte[] response = new byte[0];
		byte[] temp;

		for (int i = 0; i < blocks; i++) {
			if (method == R_W_Methods.Polling_Mode) {
				waitforI2Cwrite(100);
			} else {
				try {
					// else wait
					Thread.sleep(6);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			temp = readSRAMBlock();

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
		int index = 0;
		byte[] Data = new byte[4];
		index = 0;

		reader.SectorSelect((byte) 0);

		Data[index++] = (byte) 0x03;
		Data[index++] = (byte) 0x00;
		Data[index++] = (byte) 0xFE;
		Data[index++] = (byte) 0x00;

		reader.write(Data, (byte) 0x04);
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#writeEmptyNdef()
	 */
	@Override
	public void writeDefaultNdef() throws IOException, FormatException {
		byte[] Data = new byte[4];

		reader.SectorSelect((byte) 0);

		Data[0] = (byte) 0x03;
		Data[1] = (byte) 0x60;
		Data[2] = (byte) 0x91;
		Data[3] = (byte) 0x02;
		
		reader.write(Data, (byte) 0x04);
		
		Data[0] = (byte) 0x35;
		Data[1] = (byte) 0x53;
		Data[2] = (byte) 0x70;
		Data[3] = (byte) 0x91;
		
		reader.write(Data, (byte) 0x05);
		
		Data[0] = (byte) 0x01;
		Data[1] = (byte) 0x14;
		Data[2] = (byte) 0x54;
		Data[3] = (byte) 0x02;
		
		reader.write(Data, (byte) 0x06);
		
		Data[0] = (byte) 0x65;
		Data[1] = (byte) 0x6E;
		Data[2] = (byte) 0x4E;
		Data[3] = (byte) 0x54;
		
		reader.write(Data, (byte) 0x07);
		
		Data[0] = (byte) 0x41;
		Data[1] = (byte) 0x47;
		Data[2] = (byte) 0x20;
		Data[3] = (byte) 0x49;
		
		reader.write(Data, (byte) 0x08);

		Data[0] = (byte) 0x32;
		Data[1] = (byte) 0x43;
		Data[2] = (byte) 0x20;
		Data[3] = (byte) 0x45;
		
		reader.write(Data, (byte) 0x09);

		Data[0] = (byte) 0x58;
		Data[1] = (byte) 0x50;
		Data[2] = (byte) 0x4C;
		Data[3] = (byte) 0x4F;
		
		reader.write(Data, (byte) 0x0A);
		
		Data[0] = (byte) 0x52;
		Data[1] = (byte) 0x45;
		Data[2] = (byte) 0x52;
		Data[3] = (byte) 0x51;
		
		reader.write(Data, (byte) 0x0B);
		
		Data[0] = (byte) 0x01;
		Data[1] = (byte) 0x19;
		Data[2] = (byte) 0x55;
		Data[3] = (byte) 0x01;
		
		reader.write(Data, (byte) 0x0C);
		
		Data[0] = (byte) 0x6E;
		Data[1] = (byte) 0x78;
		Data[2] = (byte) 0x70;
		Data[3] = (byte) 0x2E;
		
		reader.write(Data, (byte) 0x0D);
		
		Data[0] = (byte) 0x63;
		Data[1] = (byte) 0x6F;
		Data[2] = (byte) 0x6D;
		Data[3] = (byte) 0x2F;
		
		reader.write(Data, (byte) 0x0E);

		Data[0] = (byte) 0x64;
		Data[1] = (byte) 0x65;
		Data[2] = (byte) 0x6D;
		Data[3] = (byte) 0x6F;
		
		reader.write(Data, (byte) 0x0F);
		
		Data[0] = (byte) 0x62;
		Data[1] = (byte) 0x6F;
		Data[2] = (byte) 0x61;
		Data[3] = (byte) 0x72;
		
		reader.write(Data, (byte) 0x10);
		
		Data[0] = (byte) 0x64;
		Data[1] = (byte) 0x2F;
		Data[2] = (byte) 0x4F;
		Data[3] = (byte) 0x4D;
		
		reader.write(Data, (byte) 0x11);
		
		Data[0] = (byte) 0x35;
		Data[1] = (byte) 0x35;
		Data[2] = (byte) 0x36;
		Data[3] = (byte) 0x39;
		
		reader.write(Data, (byte) 0x12);
		
		Data[0] = (byte) 0x54;
		Data[1] = (byte) 0x0F;
		Data[2] = (byte) 0x14;
		Data[3] = (byte) 0x61;
		
		reader.write(Data, (byte) 0x13);
		
		Data[0] = (byte) 0x6E;
		Data[1] = (byte) 0x64;
		Data[2] = (byte) 0x72;
		Data[3] = (byte) 0x6F;
		
		reader.write(Data, (byte) 0x14);

		Data[0] = (byte) 0x69;
		Data[1] = (byte) 0x64;
		Data[2] = (byte) 0x2E;
		Data[3] = (byte) 0x63;
		
		reader.write(Data, (byte) 0x15);
		
		Data[0] = (byte) 0x6F;
		Data[1] = (byte) 0x6D;
		Data[2] = (byte) 0x3A;
		Data[3]= (byte) 0x70;
		
		reader.write(Data, (byte) 0x16);

		Data[0] = (byte) 0x6B;
		Data[1] = (byte) 0x67;
		Data[2] = (byte) 0x63;
		Data[3] = (byte) 0x6F;
		
		reader.write(Data, (byte) 0x17);
		
		Data[0] = (byte) 0x6D;
		Data[1] = (byte) 0x2E;
		Data[2] = (byte) 0x6E;
		Data[3] = (byte) 0x78;
		
		reader.write(Data, (byte) 0x18);

		Data[0] = (byte) 0x70;
		Data[1] = (byte) 0x2E;
		Data[2] = (byte) 0x6E;
		Data[3] = (byte) 0x74;
		
		reader.write(Data, (byte) 0x19);
		
		Data[0] = (byte) 0x61;
		Data[1] = (byte) 0x67;
		Data[2] = (byte) 0x69;
		Data[3] = (byte) 0x32;
		
		reader.write(Data, (byte) 0x1A);
		
		Data[0] = (byte) 0x63;
		Data[1] = (byte) 0x64;
		Data[2] = (byte) 0x65;
		Data[3] = (byte) 0x6D;
		
		reader.write(Data, (byte) 0x1B);
		
		Data[0] = (byte) 0x6F;
		Data[1] = (byte) 0x5F;
		Data[2] = (byte) 0xFE;
		Data[3] = (byte) 0x00;
		
		reader.write(Data, (byte) 0x1C);
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
		int index = 0;
		byte[] Data = new byte[4];
		byte[] Eq;
		index = 0;
		
		reader.SectorSelect((byte) 0);

		// checking Capability Container
		if (getProduct() == Prod.NTAG_I2C_1k || getProduct() == Prod.NTAG_I2C_1k_Plus) {
			// CC for NTAG 1k
			Data[index++] = (byte) 0xE1;
			Data[index++] = (byte) 0x10;
			Data[index++] = (byte) 0x6D;
			Data[index++] = (byte) 0x00;

		} else if (getProduct() == Prod.NTAG_I2C_2k || getProduct() == Prod.NTAG_I2C_2k_Plus) {
			// CC for NTAG 2k
			Data[index++] = (byte) 0xE1;
			Data[index++] = (byte) 0x10;
			Data[index++] = (byte) 0xEA;
			Data[index++] = (byte) 0x00;
		} 

		// write CC
		try {
			reader.write(Data, (byte) 0x03);
		} catch (IOException e) {
			e.printStackTrace();
			throw new CC_differException(
					"Capability Container cannot be written (use I2C instead to reset)");
		}

		// check if CC are set correctly
		Eq = reader.read((byte) 0x03);
		if (!(Eq[0] == Data[0] && Eq[1] == Data[1] && Eq[2] == Data[2] && Eq[3] == Data[3])) {
			throw new CC_differException(
					"Capability Container wrong (use I2C instead to reset)");
		}

		// checking static Lock bits
		Eq = reader.read((byte) 0x02);
		if (!(Eq[2] == 0 && Eq[3] == 0)) {
			throw new StaticLockBitsException(
					"Static Lockbits set, cannot reset (use I2C instead to reset)");
		}

		// checking dynamic Lock bits
		if (getProduct() == Prod.NTAG_I2C_1k || getProduct() == Prod.NTAG_I2C_1k_Plus) {
			Eq = reader.read((byte) 0xE2);
		} else if (getProduct() == Prod.NTAG_I2C_2k) {
			reader.SectorSelect((byte) 1);
			Eq = reader.read((byte) 0xE0);
		} else if (getProduct() == Prod.NTAG_I2C_2k_Plus) {
			Eq = reader.read((byte) 0xE2);
		}

		if (!(Eq[0] == 0 && Eq[1] == 0 && Eq[2] == 0)) {
			throw new DynamicLockBitsException(
					"Dynamic Lockbits set, cannot reset (use I2C instead to reset)");
		}

		// write all zeros
		reader.SectorSelect((byte) 0);

		byte[] d = new byte[getProduct().getMemsize()];
		writeEEPROM(d, null);

		// Write empty NDEF TLV in User Memory
		writeDefaultNdef();
		
		// Bytes Written: Product Memory + Default Ndef (104 bytes)
		int bytesWritten = getProduct().getMemsize() + DEFAULT_NDEF_MESSAGE_SIZE;
		
		return bytesWritten;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#writeNDEF()
	 */
	@Override
	public void writeNDEF(NdefMessage message, WriteEEPROMListener listener) throws IOException,
            FormatException {
		byte[] Ndef_message_byte = createRawNdefTlv(message);
		writeEEPROM(Ndef_message_byte, listener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.nxp.reader.I2C_Enabled_Commands#readNDEF()
	 */
	@Override
	public NdefMessage readNDEF() throws IOException, FormatException {
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

		// get the String out of the Message
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
		byte nc_reg = getSessionRegister(SR_Offset.NC_REG);
		if ((nc_reg & NC_Reg_Func.PTHRU_ON_OFF.getValue()) == 0
				|| (nc_reg & NC_Reg_Func.PTHRU_DIR.getValue()) == 0)
			return false;

		byte ns_reg = getSessionRegister(SR_Offset.NS_REG);
		if ((ns_reg & NS_Reg_Func.RF_LOCKED.getValue()) == 0)
			return false;

		return true;
	}
}
