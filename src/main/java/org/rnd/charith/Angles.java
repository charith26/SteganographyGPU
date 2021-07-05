package org.rnd.charith;

import com.aparapi.Kernel;
import com.aparapi.Range;

public class Angles {
	
	final int length = 1000000;
	
	double[] arr_a = new double[length];
	double[] arr_b = new double[length];
	double[] arr_c = new double[length];
	
	double[] latitude1 = new double[length];
	double[] longitude1 = new double[length];
	
	double[] latitude2 = new double[length];
	double[] longitude2 = new double[length];

	public Angles() {
		for(int i=0;i<length;i++) {
			if(i==0) {
				arr_a[i] = 1.01;
				arr_b[i] = 0.812;
				arr_c[i] = 1.78;
				
				latitude1[i] = 8.350326522152482;
				longitude1[i] = 80.3961970228071;
			}
			else {
				arr_a[i] = arr_a[i-1]*2;
				arr_b[i] = arr_b[i-1]*2;
				arr_c[i] = arr_c[i-1]*2;
				latitude1[i] = latitude1[i-1]*1.1;
				longitude1[i] = longitude1[i-1]*1.1;
			}
		}
		
		gpuLoop();
	}
	
	private void cpuLoop() {
		for(int cnt=0;cnt<100;cnt++) {
			long l1 = System.currentTimeMillis();
			for(int i=0;i<length;i++) {
				double a = arr_a[i];
				double b = arr_b[i];
				double c = arr_c[i];
	
				double angle_b = (Math.pow(c, 2) + Math.pow(a, 2) - Math.pow(b, 2)) / (2 * c * a);
				angle_b = Math.toDegrees(Math.acos(angle_b));
				
				double earthRadius = 6378.1;
				double distance = a;
				
				double lat1 = Math.toRadians(latitude1[i]);
				double lon1 = Math.toRadians(longitude1[i]);
				
				double lat2 = Math.asin( Math.sin(lat1)*Math.cos(distance/earthRadius) +
						Math.cos(lat1)*Math.sin(distance/earthRadius)*Math.cos(angle_b));
				
				double lon2 = lon1 + Math.atan2(Math.sin(angle_b)*Math.sin(distance/earthRadius)*Math.cos(lat1),
						Math.cos(distance/earthRadius)-Math.sin(lat1)*Math.sin(lat2));
				
				lat2 = Math.toDegrees(lat2);
				lon2 = Math.toDegrees(lon2);
				
				latitude2[i] = lat2;
				longitude2[i] = lon2;
			}
			long l2 = System.currentTimeMillis();
			System.out.println(cnt+","+(l2-l1));
		}
	}
	
	private void gpuLoop() {
		Kernel kernel = new Kernel() {
			@Override
			public void run() {
				int id = getGlobalId();
				double a = arr_a[id];
				double b = arr_b[id];
				double c = arr_c[id];

				double angle_b = (Math.pow(c, 2) + Math.pow(a, 2) - Math.pow(b, 2)) / (2 * c * a);
				angle_b = Math.toDegrees(Math.acos(angle_b));
				//System.out.println(angle_b);
				
				double earthRadius = 6378.1;
				double distance = a;
				
				double lat1 = Math.toRadians(8.350326522152482);
				double lon1 = Math.toRadians(80.3961970228071);
				
				double lat2 = Math.asin( Math.sin(lat1)*Math.cos(distance/earthRadius) +
						Math.cos(lat1)*Math.sin(distance/earthRadius)*Math.cos(angle_b));
				
				double lon2 = lon1 + Math.atan2(Math.sin(angle_b)*Math.sin(distance/earthRadius)*Math.cos(lat1),
						Math.cos(distance/earthRadius)-Math.sin(lat1)*Math.sin(lat2));
				
				lat2 = Math.toDegrees(lat2);
				lon2 = Math.toDegrees(lon2);
				
				latitude2[id] = lat2;
				longitude2[id] = lon2;
			}
		};
		Range range = Range.create(length);
		kernel.execute(range);
		
		for(int i=0;i<100;i++) {
			long l1 = System.currentTimeMillis();
			kernel.execute(range);
			long l2 = System.currentTimeMillis();
			System.out.println(l2-l1);
		}
	}

	public static void main(String[] args) {
		new Angles();
	}
}
