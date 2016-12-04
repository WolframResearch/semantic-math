package thmp;

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
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Scanner;

import thmp.ParseState.ParseStateBuilder;
import thmp.search.CollectThm;

/**
 * Generate relational context vector, i.e. instances of RelationVec.
 * @author yihed
 *
 */
public class GenerateRelationVec {

	private static final String serializeFileName = "src/thmp/data/fieldsThmsRelationVecs.ser";
	
	public static BitSet generateRelationVec(String queryStr, ParseState parseState){
		boolean isVerbose = true;
		ParseRun.parseInput(queryStr, parseState, isVerbose);
		return parseState.getRelationalContextVec();		
	}
	
	public static void main(String[] args){
		
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();
		ParseState parseState = parseStateBuilder.build();
		List<BitSet> bitSetList = new ArrayList<BitSet>(); 
		
		List<String> bareThmList = CollectThm.ThmList.get_bareThmList();
		
		for(String thm : bareThmList){
			BitSet bitVec = generateRelationVec(thm, parseState);	
			bitSetList.add(bitVec);
		}
		
		//serialize the bitSetList
		serializeRelationVecList(bitSetList);
		
		//test deserialization
		System.out.println("Deserialized relation vectors: " + deserializeRelationVecList());
	}
	
	
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
	
	private static void serializeRelationVecList(List<BitSet> relationVecList){
		
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
	private static List<BitSet> deserializeRelationVecList(){
		
		List<BitSet> relationVecList = null;
		
		FileInputStream fi = null;
		ObjectInputStream oi = null;
		
		try{
			fi = new FileInputStream(serializeFileName);
			oi = new ObjectInputStream(fi);
		}catch(FileNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("relation vectors file not found " + e);
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while creating ObjectInputStream " + e);
		}
		finally{
			silentClose(fi);
		}
		
		try{
			Object o = oi.readObject();
			relationVecList = (List<BitSet>)o;
			/*for(BitSet relationVec : relationVecList){
				System.out.println(relationVec);
			}*/
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while reading object. " + e);			
		}catch(ClassNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("Class not found while reading object. " + e);
		}finally{
			silentClose(fi);
		}
		return relationVecList;
	}
	
	public static List<BitSet> getRelationVecList(){
		return deserializeRelationVecList();
	}
	
	/**
	 * @deprecated
	 * @param parseState
	 * @param bitSetList
	 */
	private static void readThmFromFile(ParseState parseState, List<BitSet> bitSetList){
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
				BitSet bitVec = generateRelationVec(line, parseState);	
				bitSetList.add(bitVec);
			}
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while reading from buffer: " + e);
		}finally{
			silentClose(br);
		}
	}
	
}
