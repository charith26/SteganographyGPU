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

import javax.imageio.ImageIO;

import com.aparapi.Kernel;
import com.aparapi.Range;

public class SteganographyGPU {

	public SteganographyGPU() {
	}

	/**
	 * 
	 */
	public SteganographyGPU(String inFile, String outFile, String payload, String passPhrase, String messageFile) {
		long l1 = System.nanoTime();
		encode(inFile, outFile, payload, passPhrase);
		long l2 = System.nanoTime();
		decode(outFile, messageFile, passPhrase);
		long l3 = System.nanoTime();

		System.out.println("Time to Encode: " + (l2 - l1));
		System.out.println("Time to Decode: " + (l3 - l2));
	}

	/**
	 * 
	 */
	private void encode(String inFile, String outFile, String payload, String passPhrase) {
		try {
			// read image as bytes
			BufferedImage img = ImageIO.read(new File(inFile));
			WritableRaster raster = img.getRaster();
			DataBufferByte buffer = (DataBufferByte) raster.getDataBuffer();
			final byte[] pixels = buffer.getData();

			final byte[] message = getPayload(payload);
			int length = message.length;
			final byte[] enc_msg = new byte[length];
			final byte[] encMessage = new byte[length];
			final byte[] cipher = new byte[length];

			final int hash = getReducedHash(passPhrase);

			Kernel generateCipher = new Kernel() {
				@Override
				public void run() {
					int id = getGlobalId();
					int sum = 0;
					int no = id;

					while (no != 0) {
						sum = sum + no % 10;
						no = no / 10;
					}

					int c = hash + sum;
					if (c < 48)
						c += 48;
					cipher[id] = (byte) c;
				}
			};

			Kernel encodeKernel = new Kernel() {
				@Override
				public void run() {
					int id = getGlobalId();
					enc_msg[id] = (byte) (message[id] + cipher[id]);
				}
			};

			Kernel sKernel = new Kernel() {
				int len = 0, i = 7;

				@Override
				public void run() {
					int x = getGlobalId();
					byte pixel = pixels[x];

					if (((encMessage[len] >> i) & 1) == 1) {
						pixel = (byte) (pixel | 0x1);
						pixels[x] = pixel;
					} else {
						pixel = (byte) (pixel & (~0x1));
						pixels[x] = pixel;
					}

					if (i >= 0) {
						i--;
					}
					if (i < 0) {
						i = 7;
						len++;
					}
				}
			};

			for (int i = 0; i < 25; i++) {
				long l1 = System.currentTimeMillis();

				Range range = Range.create(length);
				generateCipher.execute(range);
				encodeKernel.execute(range);

				ByteBuffer bb = ByteBuffer.allocate(4);
				bb.putInt(enc_msg.length);
				byte[] messageLength = bb.array();

				ByteArrayOutputStream bOut = new ByteArrayOutputStream();
				bOut.write(messageLength);
				bOut.write(enc_msg);

				System.arraycopy(bOut.toByteArray(), 0, encMessage, 0, length);

				if ((encMessage.length * 8) > pixels.length) {
					System.out.println("Data to hide exceeds image size");
					System.exit(0);
				}

				sKernel.execute(range);

				File file = new File(outFile);
				if (file.exists()) {
					file.delete();
				}

				ImageIO.write(img, "png", file);

				long l2 = System.currentTimeMillis();
				System.out.println(l2 - l1);
			}

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

			// read message length
			ByteBuffer ml = readBytes(pixels, 4, 0, passPhrase);
			int messageLength = ml.getInt();
			// System.out.println("Len: "+messageLength);

			// read message
			ByteBuffer msg = readBytes(pixels, messageLength, 32, passPhrase);

			final byte[] cipher = new byte[messageLength];

			final int hash = getReducedHash(passPhrase);

			Kernel generateCipher = new Kernel() {
				@Override
				public void run() {
					int id = getGlobalId();
					int sum = 0;
					int no = id;

					while (no != 0) {
						sum = sum + no % 10;
						no = no / 10;
					}

					int c = hash + sum;
					if (c < 48)
						c += 48;
					cipher[id] = (byte) c;
				}
			};

			final byte[] decMessage = new byte[messageLength];
			final byte[] enc_msg = msg.array();
			Kernel decodeKernel = new Kernel() {
				@Override
				public void run() {
					int id = getGlobalId();
					decMessage[id] = (byte) (enc_msg[id] - cipher[id]);
				}
			};

			Range range = Range.create(messageLength);
			generateCipher.execute(range);
			decodeKernel.execute(range);

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
		int i = 0, bc = 7;

		for (int x = start; i < messageLength.length; x++) {
			byte pixel = pixels[x];

			if (((pixel >> 0) & 1) == 1) {
				pixel = (byte) ((pixel >> 0) & 1);
				messageLength[i] = (byte) ((messageLength[i] << 1) ^ pixel);
				bc--;
			} else {
				pixel = (byte) ((pixel >> 0) & 0);
				messageLength[i] = (byte) ((messageLength[i] << 1) ^ pixel);
				bc--;
			}

			if (bc < 0) {
				bc = 7;
				i++;
			}
		}

		ByteBuffer bb = ByteBuffer.wrap(messageLength);
		return bb;
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

		return (int) sum;
	}

	/**
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 5) {
			SteganographyGPU s = new SteganographyGPU();

			/*for (int i = 0; i < 25; i++) {
				long l1 = System.currentTimeMillis();
				s.encode("/Users/charith/Downloads/John_Martin_Le_Pandemonium_Louvre.JPG",
						"/Users/charith/Downloads/encoded.png", "/Users/charith/Downloads/Paradise_Lost_NT.txt",
						"paradise lost");
				long l2 = System.currentTimeMillis();
				System.out.println(l2 - l1);
			}*/

			/*System.out.println("---");
			for (int i = 0; i < 25; i++) {
				long l1 = System.currentTimeMillis();
				s.decode("/Users/charith/Downloads/encoded.png", "/Users/charith/Downloads/out.txt", "paradise lost");
				long l2 = System.currentTimeMillis();
				System.out.println(l2 - l1);
			}*/

			s.encode("/Users/charith/Downloads/John_Martin_Le_Pandemonium_Louvre.JPG",
					"/Users/charith/Downloads/encoded.png", "/Users/charith/Downloads/book11.txt", "paradise lost");

			//s.decode("/Users/charith/Downloads/encoded.png", "/Users/charith/Downloads/out.txt", "paradise lost");

		} else {
			new SteganographyGPU(args[0], args[1], args[2], args[3], args[4]);
		}
	}
}
