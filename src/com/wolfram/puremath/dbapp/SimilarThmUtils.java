package com.wolfram.puremath.dbapp;

import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wolfram.puremath.dbapp.DBUtils.SimilarThmsTb;

import thmp.parse.InitParseWithResources;

/**
 * DB utils used for incorporating similar theorems indices
 * into the database.
 * 
 * ISO-8859-1
 * 
 * @author yihed
 *
 */
public class SimilarThmUtils {
	
	/**max length for index string, in units of bytes (not chars). 
	 * 100 similar thms, need 100*20/8 = 250 bytes/ISO-8859-encoded chars.
	 * Add 1 for padding after rounding */
	private static final int MAX_INDEX_STR_LEN;
	
	private static final int MAX_THM_INDEX_LIST_LEN;
	
	/**This changes as the number of total thms changes,
	 * 20 if num thms below 1 mil. 
	 * Update Dec 4: now at 1.34 million, so 21*/
	private static final int NUM_BITS_PER_THM_INDEX = 21;
	
	private static final int NUM_BITS_PER_BYTE = 8;
	
	private static final Charset INDEX_STR_CHAR_SET = Charset.forName("ISO-8859-1");
	
	private static final boolean DEBUG = thmp.utils.FileUtils.isOSX() ? InitParseWithResources.isDEBUG() : false;
	//figure out why above ant script doesn't recognize path!!!
	//private static final boolean DEBUG = false;
	private static final Logger logger = LogManager.getLogger(SimilarThmUtils.class);
	
	static {
		MAX_THM_INDEX_LIST_LEN = 100;
		/**max length for index string. 
		 * 100 similar thms, need 100*20/8 = 250 bytes/ISO-8859-encoded chars.
		 * Add 2 for padding*/
		final int padding = 2;
		int strLen = MAX_THM_INDEX_LIST_LEN * NUM_BITS_PER_THM_INDEX / NUM_BITS_PER_BYTE + padding;
		MAX_INDEX_STR_LEN = strLen;
	}
	
	/** 
	 * Total 770k thms, so each index needs 20 bits. 
	 */
	/**
	 * Convert string to index list.
	 * @param indexStr
	 * @return
	 */
	public static List<Integer> strToIndexList(String str, int NUM_BITS_PER_INDEX) {
		
		//List<Byte> byteList = new ArrayList<Byte>();
		
		int byteArrayLen = str.length();
		byte[] byteAr = new byte[byteArrayLen];
		
		for(int i = 0; i < byteArrayLen; i++) {			
			//byteList.add((byte)str.charAt(i));
			byteAr[i] = (byte)str.charAt(i);
		}
		
		if(DEBUG) System.out.println("strLen "+byteArrayLen + " "+str);
		
		return byteArrayToIndexList(byteAr, NUM_BITS_PER_INDEX);
	}

	public static List<Integer> byteArrayToIndexList(byte[] byteAr, int numBitsPerIndex) {
	
		List<Integer> thmIndexList = new ArrayList<Integer>();
		int byteArrayLen = byteAr.length;
		int totalBitsLen = NUM_BITS_PER_BYTE * byteArrayLen;
		
		int numTotal = totalBitsLen/numBitsPerIndex;
		/* Note: must discard remainder in this division, since the bits padding was put in
		 * to reach a multiple of 8. 
		 */
		
		for(int i = 0; i < numTotal; i++) {
			thmIndexList.add(getIndex(byteAr, i, numBitsPerIndex));	
		}
		return thmIndexList;
	}
	
	//curTupleIndex is 0-based
	private static int getIndex(byte[] byteAr, int curTupleIndex, int numBitsPerIndex) {
		
		List<Byte> zeroOneList = getZeroOneList(byteAr, curTupleIndex, numBitsPerIndex);
		int thmIndex = 0;
		if(DEBUG) {
			System.out.println("zeroOneList.size() "+zeroOneList.size());
			System.out.println("zeroOneList "+zeroOneList);
		}
		for(byte power : zeroOneList) {
			thmIndex += 1 << power;				
		}
		return thmIndex;
	}
	
	/**
	 * Fill in the next 20 bits of the byteAr.
	 * @param byteAr
	 * @param curTupleIndex
	 * @param numBitsPerIndex number of bits per (e.g. thm, word) index.
	 * @return
	 */
	private static List<Byte> getZeroOneList(byte[] byteAr, int curTupleIndex, int numBitsPerIndex) {
		
		//the next 21-bit thmIndex starts at index inArrayShift in the byte array
		int inArrayShift = curTupleIndex * numBitsPerIndex / NUM_BITS_PER_BYTE;
		//the next 21-bit thmIndex starts at index inByteShift in current byte
		byte inByteShift = (byte)(curTupleIndex * numBitsPerIndex - NUM_BITS_PER_BYTE * inArrayShift);
		
		if(DEBUG) {
			System.out.println("inByteShift "+inByteShift + " inArrayShift "+inArrayShift);
		}
		
		List<Byte> zeroOneList = new ArrayList<Byte>();
		int curBitCounter = 0;
		byte curByte = byteAr[inArrayShift];
		int bitDivider = NUM_BITS_PER_BYTE - inByteShift;
		
		byte remainder = curByte;
		//!=0 instead of >=0, since 11111111 will be compared as -1 instead 255.
		/*while(remainder != 0) {
			byte power = (byte)(Math.log(remainder) / Math.log(2));			
			if(power < inByteShift) {
				break;
			}	
			zeroOneList.add((byte)(power - inByteShift));
			remainder -= Math.pow(2, power);
		}*/
		
		for(byte j = inByteShift; j < NUM_BITS_PER_BYTE; j++) {
			if((remainder >> j & 1) == 1) {
				byte curBitToSet = (byte)(j - inByteShift);				
				zeroOneList.add(curBitToSet);
			}
		}
		
		curBitCounter += bitDivider;
		
		/*for( ; curBitCounter < bitDivider ; curBitCounter++) {			
			byte tempRem = (byte)(Math.log(curByte) / Math.log(2)); //HERE
			if(tempRem >= 0) {
				zeroOneAr[8 - curBitCounter] = 1;
				remainder = tempRem;
				if(tempRem == 0) {
					break;
				}
			}
		}*/
		final int maxNumBytesSpan = (int)Math.ceil(((double)numBitsPerIndex)/NUM_BITS_PER_BYTE);
		//4 because 3 is the maximal number of bytes NUM_BITS_PER_INDEX spans
		outerFor: for(int i = 1; i <= maxNumBytesSpan && curBitCounter < numBitsPerIndex; i++) {
			curByte = byteAr[inArrayShift+i];
			remainder = curByte;
			
			for(byte j = 0; j < NUM_BITS_PER_BYTE; j++) {
				if((remainder >> j & 1) == 1) {
					byte curBitToSet = (byte)(j + bitDivider + NUM_BITS_PER_BYTE*(i-1));
					
					if(curBitToSet >= numBitsPerIndex) {
						break outerFor;
					}
					zeroOneList.add(curBitToSet);
				}
			}			
			/*while(remainder != 0) {
				byte power = (byte)(Math.log(remainder) / Math.log(2));
				byte curBitToSet = (byte)(power + bitDivider + 8*(i-1));				
				if(curBitToSet >= 20) {
					break outerFor;
				}
				zeroOneList.add(curBitToSet);		
				remainder -= Math.pow(2, power);
			}	*/
			curBitCounter += NUM_BITS_PER_BYTE;
		}
		return zeroOneList;
	}
	
	/**
	 * Encode list of indices to string.
	 * Total 770k thms, so each index needs log(2,770k) = 20 bits. For
	 * say top 100 similar thms, need 100*20/8 = 250 bytes/ISO-8859-encoded chars.
	 * @param thmIndexList
	 * @return
	 */
	public static String indexListToStr(List<Integer> thmIndexList) {
		
		byte[] indexByteAr = indexListToByteArray(thmIndexList);
		return new String(indexByteAr, INDEX_STR_CHAR_SET);
	}

	public static byte[] indexListToByteArray(List<Integer> indexList) {
		return indexListToByteArray(indexList, MAX_THM_INDEX_LIST_LEN);
	}
	
	/**
	 * Encode list of indices to string.
	 * Total 770k thms, so each index needs log(2,770k) = 20 bits. For
	 * say top 100 similar thms, need 100*20/8 = 250 bytes/ISO-8859-encoded chars.
	 * @param indexList
	 * @param maxIndexListLen max length of index list to truncate to.
	 * @return
	 */
	public static byte[] indexListToByteArray(List<Integer> indexList, int maxIndexListLen) {
		
		int thmIndexListLen = indexList.size();
		thmIndexListLen = thmIndexListLen > maxIndexListLen ? maxIndexListLen : thmIndexListLen;
		
		//create byte array, then turn byte array into string		
		byte[] indexByteAr = new byte[thmIndexListLen*NUM_BITS_PER_THM_INDEX/NUM_BITS_PER_BYTE+1];
		
		for(int i = 0; i < thmIndexListLen; i++) {						
			fillByteArray(indexByteAr, indexList.get(i), i, NUM_BITS_PER_THM_INDEX);
		}
		if(DEBUG) System.out.println("indexByteAr "+Arrays.toString(indexByteAr));
		return indexByteAr;
	}
	
	/**
	 * Turn list of integer indices to byte array. To optimize db storage and performance.
	 * @param indexList
	 * @param numBitsPerIndex
	 * @param maxWordsIndexListLen
	 * @return
	 */
	public static byte[] indexListToByteArray(List<Integer> indexList, int numBitsPerIndex,
			int maxWordsIndexListLen) {
		int thmIndexListLen = indexList.size();
		if(thmIndexListLen > maxWordsIndexListLen) {
			thmIndexListLen = maxWordsIndexListLen;
		}
		//create byte array, then turn byte array into string
		
		byte[] indexByteAr = new byte[thmIndexListLen*numBitsPerIndex/NUM_BITS_PER_BYTE+1];
		
		for(int i = 0; i < thmIndexListLen; i++) {						
			fillByteArray(indexByteAr, indexList.get(i), i, numBitsPerIndex);
		}
		if(DEBUG) System.out.println("indexByteAr "+Arrays.toString(indexByteAr));
		return indexByteAr;
	}
	
	/**
	 *add byte to array , along with shift 
	 * @param indexByteAr
	 * @param thmIndex
	 * @param thmCount the count of the current thm amongst the similar thms.
	 * thmCount is 0-based, i.e. starts counting at 0.
	 */
	private static void fillByteArray(byte[] indexByteAr, int thmIndex, int thmCount, 
			int numBitsPerIndex) {
		
		//higher-indexed bits represent higher powers of 2.
		byte[] zeroOneAr = new byte[numBitsPerIndex];
		int remainder = thmIndex;
		for(int p = numBitsPerIndex-1; p > -1; p--) {
			int tempRem = remainder - (1 << p);
			if(tempRem >= 0) {
				zeroOneAr[p] = 1;
				remainder = tempRem;
				if(tempRem == 0) {
					break;
				}
			}			
		}	
		int inArrayShift = thmCount * numBitsPerIndex / NUM_BITS_PER_BYTE;
		/* amount of shift in the byte partially filled from last index.
		 * 8 means*/
		int inByteShift = thmCount * numBitsPerIndex - inArrayShift * NUM_BITS_PER_BYTE;
		
		//current bit index between 0 and NUM_BITS_PER_INDEX
		int curBitIndex = 0;
		//at most 3 dividers fit within 20 bits, which span at most 4 bytes.
		//divider indices are starting indices of next byte.
		
		int firstDivider = NUM_BITS_PER_BYTE - inByteShift;
		
		byte curByte = indexByteAr[inArrayShift];
		
		for(int i = 0; i < 4; i++) {		
			for(; curBitIndex < firstDivider+NUM_BITS_PER_BYTE*i && curBitIndex < numBitsPerIndex; curBitIndex++) {
				if(zeroOneAr[curBitIndex] == 1) {
					curByte |= (1 << (curBitIndex + inByteShift - NUM_BITS_PER_BYTE*i));
				}
			}
			
			indexByteAr[inArrayShift+i] = curByte;
			if(curBitIndex >= numBitsPerIndex) {
				break;
			}
			curByte = 0;
		}
	}	
	
	/**
	 * Retrieve indices from DB.
	 * @param thmIndex
	 * @param conn
	 * @return
	 * @throws SQLException
	 */
	public static List<Integer> getSimilarThmsListFromDb(int thmIndex, Connection conn) throws SQLException {
		
		StringBuilder sb = new StringBuilder(50);
		sb.append("SELECT ").append(SimilarThmsTb.SIMILAR_THMS_COL)
		.append(" FROM ").append(SimilarThmsTb.TB_NAME)
		.append(" WHERE ").append(SimilarThmsTb.INDEX_COL)
		.append("=").append(thmIndex).append(";");
		
		return queryByteArray(conn, sb, NUM_BITS_PER_THM_INDEX);
	}

	/**
	 * Executes the supplied query using the supplied connection.
	 * @param conn
	 * @param querySb
	 * @param numBitsPerIndex
	 * @return
	 * @throws SQLException
	 */
	public static List<Integer> queryByteArray(Connection conn, StringBuilder querySb, int numBitsPerIndex) 
			throws SQLException {
		
		PreparedStatement pstm = conn.prepareStatement(querySb.toString());
		
		ResultSet rs = pstm.executeQuery();
		byte[] indexBytes;
		if(rs.next()) {
			indexBytes = rs.getBytes(1);
		}else {
			pstm.close();
			return Collections.emptyList();
		}
		pstm.close();
		rs.close();
		return byteArrayToIndexList(indexBytes, numBitsPerIndex);
	}
	
	
	/**
	 * max length for index string used in db, in units of bytes (not chars). 
	 * 100 similar thms, need 100*20/8 = 250 bytes/ISO-8859-encoded chars.
	 * Add 1 for padding after rounding 
	 * @return
	 */
	public static int maxSimilarThmListStrLen() {
		return MAX_INDEX_STR_LEN;
	}
	
	public static int maxSimilarThmListLen() {
		return MAX_THM_INDEX_LIST_LEN;
	}
	
	public static void main(String[] args) {
		
		List<Integer> thmIndexList = new ArrayList<Integer>();
		//thmIndexList.add(770000);
		//40890, 570
		//[40890, 570, 1180, 821, 1034, 3651, 180]
		//-> returned index: [2203578, 560, 1180, 821, 1034, 3651, 180]
		//thmIndexList.add(40890);
		
		//thmIndexList.add(570);
		
		//thmIndexList.add(4);
		
		Random rand = new Random();
		
		for(int i= 0; i  < 10; i++) {
			//thmIndexList.add(1000000);
			thmIndexList.add(rand.nextInt(9000));
		}
		
		//[14785, 269450, 261259, 347843, 131814, 825307, 709281, 608272, 495579, 428534]
		Integer[] ar = new Integer[] {14785, 269450, 261259, 347843, 131814, 825307, 709281, 608272, 495579, 428534};
		//ar = new Integer[] {14785, 269450, 261259, 347843};
		ar = new Integer[] {1, 1, 1, 1978430};
		//ar = new Integer[] {147850, 347843};
		thmIndexList = Arrays.asList(ar);
		System.out.println(thmIndexList);
		String str = indexListToStr(thmIndexList);
		
		System.out.println("index str: "+str);
		List<Integer> returnedIndex = strToIndexList(str, NUM_BITS_PER_THM_INDEX);
		int thmIndexListSz = thmIndexList.size();
		for(int i = 0; i < thmIndexListSz; i++) {
			if(thmIndexList.get(i).intValue() != returnedIndex.get(i).intValue()) {
				System.out.println(thmIndexList.get(i) + " not equal to " + returnedIndex.get(i) + "!");
			}
		}		
		System.out.println("returned index: "+returnedIndex);
	}
	
	public static int numBitsPerThmIndex() {
		return NUM_BITS_PER_THM_INDEX;
	}
}
