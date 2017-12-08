package thmp.runner;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wolfram.puremath.dbapp.DBUtils;

import thmp.utils.FileUtils;

/**
 * Combine serialized similar theorems lists.
 * 
 * @author yihed
 */
public class CombineSimilarThms {

	public static void main(String[] args) {
		
		combineSerialFiles();
	}
	
	/**
	 * Combines different serialized files, and 
	 * 
	 */
	private static void combineSerialFiles() {
		String dirPath = DBUtils.SimilarThmsTb.similarThmIndexByteArrayDirPath;
		//get all the files in directory
		File dir = new File(dirPath);
		File[] fileAr = dir.listFiles();
		if(null == fileAr) {
			System.out.println("Couldn't find files in " + dirPath);
			return;
		}
		
		Map<Integer, byte[]> combinedMap = new HashMap<Integer, byte[]>();
		for(File file : fileAr) {
			String fileName = file.getAbsolutePath();
			@SuppressWarnings("unchecked")
			List<Map<Integer, byte[]>> mapList = (List<Map<Integer, byte[]>>)FileUtils.deserializeListFromFile(fileName);
			
			combinedMap.putAll(mapList.get(0));			
		}
		
		String combinedByteArrayPath = DBUtils.SimilarThmsTb.similarThmCombinedIndexByteArrayPath;
		List<Map<Integer, byte[]>> combinedMapList = new ArrayList<Map<Integer, byte[]>>();
		combinedMapList.add(combinedMap);
		FileUtils.serializeObjToFile(combinedMapList, combinedByteArrayPath);
		
	}
	
}
