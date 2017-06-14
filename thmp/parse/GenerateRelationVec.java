package thmp.parse;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Scanner;

import thmp.parse.ParseState.ParseStateBuilder;
import thmp.parse.RelationVec.RelationType;
import thmp.search.CollectThm;

/**
 * Generate relational context vector, i.e. instances of RelationVec.
 * @author yihed
 *
 */
public class GenerateRelationVec {

	//private static final String serializeFileName = "src/thmp/data/fieldsThmsRelationVecs.dat";
	private static final String serializeFileName = "src/thmp/data/AllThmsRelationVecs.dat";
	//private static final List<BigInteger> relationVecList;
	
	static{
		//if timestamp on serializeFileName is outdated, parse and generate vectors
		//again, else skip.
		/*int maxNumHoursLag = 10;
		int numMiliSecondsInHour = 3600000;
		File serializationFile = new File(serializeFileName);
		
		if((System.currentTimeMillis() - serializationFile.lastModified()) > numMiliSecondsInHour*maxNumHoursLag){
			parseAndSerializeThms();
		}
		relationVecList = deserializeRelationVecList();*/
	}
	
	/**
	 * Generate relational vector based on the query String. Should only
	 * be called on the query string during search.
	 * @param queryStr
	 * @param parseState
	 * @return
	 */
	public static BigInteger generateRelationVec(String queryStr, ParseState parseState){
		boolean isVerbose = true;
		ParseRun.parseInput(queryStr, parseState, isVerbose);
		return parseState.getRelationalContextVec();		
	}
	
	/**
	 * Back up vector, if generateRelationVec() did not set anything, now
	 * assume all relations are set, since none was specified.
	 * Actually should just skip searching this way if no good vector produced.
	 * @return
	 */
	/*public static BigInteger generateBackupRelationVec(String queryStr){
		
		setBitPosList(String termStr, List<Integer> bitPosList, int modulus, 
				RelationType posTermRelationType, boolean isParseStructTypeHyp);		
		fillByteArray(byte[] byteArray, List<Integer> bitPosList);		
	}*/
	
	public static void main(String[] args){
		
		//parseAndSerializeThms();
		
		//test deserialization
		//System.out.println("Deserialized relation vectors: " + deserializeRelationVecList());
	}

	/**
	 * 
	 */
	/*private static void parseAndSerializeThms() {
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();
		
		List<BigInteger> bigIntList = new ArrayList<BigInteger>(); 
		
		List<String> bareThmList = CollectThm.ThmList.get_webDisplayThmList();
		//System.out.println("bareThmList" + bareThmList);
		for(String thm : bareThmList){
			ParseState parseState = parseStateBuilder.build();
			BigInteger bitVec = generateRelationVec(thm, parseState);	
			bigIntList.add(bitVec);
		}
		
		//serialize the bitSetList. Should run this during static initializer
		//in PRD.
		serializeRelationVecList(bigIntList);
	}	*/
	
	/**
	 * Silent so not to clobber the original stack trace.
	 * @param br
	 */
	private static void silentClose(Closeable br){
		
		if(null == br) return;
		try{				
			br.close();				
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	private static void serializeRelationVecList(List<BigInteger> relationVecList){
		
		FileOutputStream fo = null;
		ObjectOutputStream oo = null;
		
		try{
			fo = new FileOutputStream(serializeFileName);
			oo = new ObjectOutputStream(fo);		
			oo.writeObject(relationVecList);
		}catch(IOException e){
			e.printStackTrace();
		}finally{
			silentClose(oo);
			silentClose(fo);
		}
		
	}
	
	/**
	 * Deserializes the relationVecList.
	 */
	@SuppressWarnings("unchecked")
	private static List<BigInteger> deserializeRelationVecList(){
		
		List<BigInteger> relationVecList = null;
		
		FileInputStream fileInputStream = null;
		ObjectInputStream objectInputStream = null;
		
		try{
			fileInputStream = new FileInputStream(serializeFileName);
			objectInputStream = new ObjectInputStream(fileInputStream);
		}catch(FileNotFoundException e){
			e.printStackTrace();
			silentClose(fileInputStream);
			silentClose(objectInputStream);
			throw new IllegalStateException("relation vectors file not found " + e);
		}catch(IOException e){
			e.printStackTrace();
			silentClose(fileInputStream);
			silentClose(objectInputStream);
			throw new IllegalStateException("IOException while creating ObjectInputStream " + e);
		}		
		
		try{
			Object o = objectInputStream.readObject();
			
			relationVecList = (List<BigInteger>)o;
			
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while reading object. " + e);	
		}catch(ClassNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("Class not found while reading object. " + e);
		}finally{
			silentClose(fileInputStream);
			silentClose(objectInputStream);
		}
		return relationVecList;
	}
	
	/*
	public static List<BigInteger> getRelationVecBitSetList(){
		return deserializeRelationVecList();
	}*/
	
	/*Should be gotten from CollectThm.ThmList.allThmsRelationVecList()
	 * public static List<BigInteger> getRelationVecList(){
		return relationVecList;
	}*/
	
	/**
	 * Commented out Dec 2016.
	 * @deprecated
	 * @param parseState
	 * @param bitSetList
	 */
	/*private static void readThmFromFile(ParseState parseState, List<BigInteger> bitSetList){
		FileReader fileReader = null;
		BufferedReader br = null;
		
		try{
			fileReader = new FileReader("src/thmp/data/fieldsThms.txt");			
		}catch(FileNotFoundException e){			
			e.printStackTrace();
			throw new IllegalStateException(e);
		}
		
		//boolean isVerbose = true;
		String line;
		try{
			br = new BufferedReader(fileReader);
			while((line = br.readLine()) != null){				
				BigInteger bitVec = generateRelationVec(line, parseState);	
				bitSetList.add(bitVec);
			}
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while reading from buffer: " + e);
		}finally{
			silentClose(br);
		}
	}*/
	
}
