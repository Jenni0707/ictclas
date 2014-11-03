package org.ictclas4j.test;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import org.ictclas4j.bean.SegResult;
import org.ictclas4j.segment.SegTag;

public class IcTest {
	private static SegTag st;
	private static SegResult sr;

	public static SegResult getSr() {
		return sr;
	}

	public static void setSr(SegResult sr) {
		IcTest.sr = sr;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		st = new SegTag(1);
		String src = null;
		try {
			src = read("test\\test1.txt");
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			sr = st.split(src);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		setSr(sr);
		System.out.println(sr.getFinalResult());
		//getWord(sr.getFinalResult());

	}/**/

	public static String read(String filePath) throws IOException {
		try {
			BufferedReader br = new BufferedReader(new FileReader(filePath));
			String s = null;
			if ((s = br.readLine()) != null) {
				return s;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void getWord(String src) {
		ArrayList<String> list = new ArrayList<String>();
		boolean condition = true;
		while (condition) {
			if (src.length() != 0) {
				int start = src.indexOf(" ");
				String word = src.substring(0, start);
				System.out.println(word);
				int d = word.indexOf("/");
				//String last = word.substring(0, d);
			//	System.out.println(last);
				src = src.substring(start + 1);
				//getWord(src);
			}
			condition = false;
		}
	}
	
}
