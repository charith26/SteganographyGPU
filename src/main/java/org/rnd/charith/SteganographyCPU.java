package org.rnd.charith;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.imageio.ImageIO;

public class SteganographyCPU {
	
	public SteganographyCPU() {
	}
	
	/**
	 * 
	 */
	public SteganographyCPU(String inFile, String outFile, String payload, String passPhrase, String messageFile) {
		long l1 = System.nanoTime();
		encode(inFile, outFile, payload, passPhrase);
		long l2 = System.nanoTime();
		decode(outFile, messageFile, passPhrase);
		long l3 = System.nanoTime();
		
		System.out.println("Time to Encode: "+(l2-l1));
		System.out.println("Time to Decode: "+(l3-l2));
	}
	
	/**
	 * 
	 */
	private void encode(String inFile, String outFile, String payload, String passPhrase) {
		try {
			//read image as bytes
			BufferedImage img = ImageIO.read(new File(inFile));
			WritableRaster raster = img.getRaster();
			DataBufferByte buffer = (DataBufferByte) raster.getDataBuffer();
			byte[] pixels = buffer.getData();
			
			byte[] message = getPayload(payload);
			byte[] encMessage = new byte[message.length];
			int length = message.length;// 20572344;
			byte[] password = passPhrase.getBytes();
			byte[] cipher = Arrays.copyOf(password, length);
			
			int hash = getReducedHash(passPhrase);
			createCipher(length, cipher, hash);
			encrypt(message, cipher, encMessage);
			
			ByteBuffer bb = ByteBuffer.allocate(4);
			bb.putInt(encMessage.length);
			//System.out.println("Len: "+encMessage.length);
			byte[] messageLength = bb.array();
			
			ByteArrayOutputStream bOut = new ByteArrayOutputStream();
			bOut.write(messageLength);
			bOut.write(encMessage);

			encMessage = bOut.toByteArray();
			
			int len = 0, i = 7;
			
			if((encMessage.length*8)>pixels.length) {
				System.out.println("Data to hide exceeds image size");
				System.exit(0);
			}
			
			for(int x=0;len<encMessage.length;x++) {
				byte pixel = pixels[x];
				
				if( ((encMessage[len] >> i) & 1) == 1 ) {
					pixel = (byte)(pixel | 0x1);
					pixels[x] = pixel;
				} 
				else {
					pixel = (byte)(pixel & (~0x1));
					pixels[x] = pixel;
				}
				
				if(i>=0) {
					i--;
				}
                if(i<0) {	
                	i=7;
                	len++;
                }
			}
			
			File file = new File(outFile);
			if(file.exists()) {
				file.delete();
			}
			
			ImageIO.write(img, "png", file);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @return
	 */
	private byte[] getPayload(String payload) throws Exception {
		File file = new File(payload);
		
		byte[] bytes = new byte[(int) file.length()];
		DataInputStream dataInputStream = new DataInputStream(new FileInputStream(file));
		dataInputStream.readFully(bytes);
		
		dataInputStream.close();
		return bytes;
	}
	
	/**
	 * 
	 * @return
	 */
	private void decode(String outFile, String messageFile, String passPhrase) {
		try {
			BufferedImage img = ImageIO.read(new File(outFile));
			WritableRaster raster = img.getRaster();
			DataBufferByte buffer = (DataBufferByte) raster.getDataBuffer();
			byte[] pixels = buffer.getData();
			
			//read message length
			ByteBuffer ml = readBytes(pixels, 4, 0, passPhrase);
			int messageLength = ml.getInt();
			//System.out.println("Len: "+messageLength);
			
			//read message
			ByteBuffer msg = readBytes(pixels, messageLength, 32, passPhrase);
			
			byte[] password = passPhrase.getBytes();
			byte[] cipher = Arrays.copyOf(password, messageLength);
			
			int hash = getReducedHash(passPhrase);
			createCipher(messageLength, cipher, hash);
			byte[] decMessage = new byte[messageLength];
			decrypt(msg.array(), cipher, decMessage);
			
			FileOutputStream fw = new FileOutputStream(messageFile);
			fw.write(decMessage);
			fw.flush();
			fw.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param pixels
	 * @return
	 */
	private ByteBuffer readBytes(byte[] pixels, int length, int start, String passPhrase) {
		byte[] messageLength = new byte[length];
		int i=0, bc = 7;
		
		for(int x=start;i<messageLength.length;x++) {
			byte pixel = pixels[x];
			
			if( ((pixel >> 0) & 1) == 1 ) {
				pixel = (byte)((pixel >> 0) & 1);
				messageLength[i] = ( byte)((messageLength[i] << 1) ^ pixel);
				bc--;
			}
			else {
				pixel = (byte)((pixel >> 0) & 0);
				messageLength[i] = ( byte)((messageLength[i] << 1) ^ pixel);
				bc--;
			}
			
			if(bc<0) {
				bc=7;
				i++;
			}
		}
		
		ByteBuffer bb = ByteBuffer.wrap(messageLength);
		return bb;
	}
	
	/**
	 * 
	 * @param message The message to be encrypted
	 * @param cipher the cipher generated based on the password. Same length as message
	 * @param enc_msg the encrypted message
	 */
	private void encrypt(byte[] message, byte[] cipher, byte[] enc_msg) {
		for (int id = 0; id < message.length; id++) {
			enc_msg[id] = (byte) (message[id] + cipher[id]);
		}
	}
	
	/**
	 * 
	 * @param enc_msg
	 * @param cipher
	 * @param dec_msg
	 */
	private void decrypt(byte[] enc_msg, byte[] cipher, byte[] dec_msg) {
		for (int id = 0; id < enc_msg.length; id++) {
			dec_msg[id] = (byte) (enc_msg[id] - cipher[id]);
		}
	}
	
	/**
	 * 
	 * @param password
	 * @return
	 */
	private int getReducedHash(String password) {
		long no = password.hashCode();
		long sum = 0;
		while (no != 0) {
			sum = sum + no % 10;
			no = no / 10;
		}
		
		return (int)sum;
	}
	
	/**
	 * 
	 * @param length
	 * @param cipher
	 */
	private void createCipher(int length, byte[] cipher, int hash) {
		for (int i = 0; i < length; i++) {
			int sum = 0;
			int no = i;

			while (no != 0) {
				sum = sum + no % 10;
				no = no / 10;
			}

			int c = hash + sum;
			if (c < 48)
				c += 48;
			cipher[i] = (byte) c;
		}
	}
	
	/**
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		if(args.length<5) {
			SteganographyCPU s = new SteganographyCPU();
			
			/*for(int i=0;i<50;i++) {
				long l1 = System.currentTimeMillis();
				s.encode(	"/Users/charith/Downloads/John_Martin_Le_Pandemonium_Louvre.JPG", 
							"/Users/charith/Downloads/encoded.png", 
							"/Users/charith/Downloads/Paradise_Lost_NT.txt", "paradise lost");
				long l2 = System.currentTimeMillis();
				System.out.println(i+","+(l2-l1));
			}*/
			/*System.out.println("---");
			for(int i=0;i<25;i++) {
				long l1 = System.currentTimeMillis();
				s.decode(	"/Users/charith/Downloads/encoded.png", 
							"/Users/charith/Downloads/out.txt", 
							"paradise lost");
				long l2 = System.currentTimeMillis();
				System.out.println(l2-l1);
			}*/
			
			/*s.encode("/Users/charith/Downloads/John_Martin_Le_Pandemonium_Louvre.JPG", 
					 "/Users/charith/Downloads/encoded.png", 
					 "/Users/charith/Downloads/pg41537-images.epub", "paradise lost");*/
			
			s.decode(	"/Users/charith/Downloads/1*6tFUvTjjdqvCIB0sm5-wcA.png", 
					"/Users/charith/Downloads/out.txt", 
					"paradise lost");
			
		}
		else {
			new SteganographyCPU(args[0], args[1], args[2], args[3], args[4]);
		}
	}
}
