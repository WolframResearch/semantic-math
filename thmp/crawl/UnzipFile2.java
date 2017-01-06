package thmp.crawl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

//import net.lingala.zip4j.core.ZipFile;
//import net.lingala.zip4j.exception.ZipException;

import thmp.ThmInput;

/**
 * Unzips files, extract math directories, and reads the latex files from them.
 * 
 * @author yihed
 *
 */
public class UnzipFile2 {

	private static final Pattern TXT_PATTERN = Pattern.compile("([^.]*)(\\.txt)");
	
	/**
	 * Retrieves list of filenames in this directory. In this case .gz files to
	 * be uncompressed.
	 * 
	 * @return
	 * @param source
	 *            directory whose files are to be checked.
	 */
	private static List<String> getFileNames(String srcDir) {
		//
		List<String> fileNames = new ArrayList<String>();
		File dir = new File(srcDir);
		if (!dir.isDirectory()) {
			System.out.println(srcDir + " is not a directory!");
			throw new IllegalStateException("Source directory" + srcDir + " is not a directory!");
		}
		File[] files = dir.listFiles();
		//System.out.println("srcDir inside getFileNames " + srcDir);
		for (File file : files) {
			String fileName = file.getName();
			//System.out.println("reading in fileName inside getFileNames(): " + fileName);
			// only append ones with .gz extension
			if (fileName.matches(".*\\.gz$")) {
				fileNames.add(fileName);
			}
		}
		return fileNames;
	}

	/**
	 * Unzips gz file. List of .gz fileNames, to be appended to the base path.
	 * @return list of fileNames of extracted files.
	 */
	private static List<String> unzipGz(String srcBasePath, String destBasePath, List<String> fileNames) {
		if (fileNames.isEmpty()){
			System.out.println("List of files is empty!");
			return null;
		}
		//System.out.println("filenames inside unzipGz()" + fileNames); 
		List<String> extractedFileNames = new ArrayList<String>();

		// length of buffer read in, optimal size?
		byte[] buf = new byte[1024];
		try {
			// fileNames is non-empty list.
			GZIPInputStream gzipInputStream = null;
			FileOutputStream gzipOutputStream = null;
			//output stream for the output in all the .gz files.
			//String totalOutputStr = destBasePath + "total.txt";
			//FileOutputStream totalOutputStream = new FileOutputStream(totalOutputStr);
			
			for (String fileName : fileNames) {
				// if not .gz file or a math file
				if (!fileName.matches("math[^.]*\\.gz$")) {
					//System.out.println("Not a .gz or math file!");
					continue;
				}
				//System.out.print("Current file being unzipped: " + fileName + "\t");
				String src = srcBasePath + fileName;
				//name the output file to be the same, but with .txt extension.
				String dest = destBasePath + fileName.replaceAll("([^\\.]*).gz$", "$1.txt");
				extractedFileNames.add(dest);

				gzipInputStream = new GZIPInputStream(new FileInputStream(src));
				gzipOutputStream = new FileOutputStream(dest);
				
				int l;
				while ((l = gzipInputStream.read(buf)) > 0) {
					// 0 is the offset, l is the length to read/write.
					gzipOutputStream.write(buf, 0, l);
					//totalOutputStream.write(buf, 0, l);
				}
				
				gzipInputStream.close();
				gzipOutputStream.close();
			}
			
			//totalOutputStream.close();

		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalStateException(Arrays.toString(e.getStackTrace()));
		}
		return extractedFileNames;
	}

	/**
	 * Reads in a directory, e.g. 0002, should be un-tar'ed directory containing many
	 * .gzip (.gz) files. 
	 * 
	 * @param args Directory path
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static void main(String[] args) throws FileNotFoundException, IOException {
		System.out.println(System.getProperty("user.dir"));
		
		if(args.length == 0){
			System.out.println("Supply a directory of gzip files!");
			return;
		}
		
		String srcBasePath = System.getProperty("user.dir") + "/" + args[0];
		String destBasePath = srcBasePath + "Content/";
		System.out.println("destBasePath: " + destBasePath);
		srcBasePath = srcBasePath + "/";
		System.out.println("srcBasePath: " + srcBasePath);
		//make base directory that the will contain the extracted files
		new File(destBasePath).mkdirs();
		
		/* OR
		 * Path path = Paths.get("C:\\Directory1");
		 * Files.createDirectories(path);
		 */
		
		// get all file names in the directory, eg directory "/0002/"
		//or "1006_005/1006"
		List<String> fileNames = getFileNames(srcBasePath);
		//System.out.println(fileNames);
		// list of files we just extracted. These should be .txt files.
		List<String> extractedFiles = unzipGz(srcBasePath, destBasePath, fileNames);
		
		boolean f = false;
		if(f){
			List<String> totalTextList = new ArrayList<String>();
			/*FileWriter fw = new FileWriter("outfilename", true);
		    BufferedWriter bw = new BufferedWriter(fw);
		    PrintWriter out = new PrintWriter(bw));
		    out.println("thm");*/
	
			// reads in those files and extract theorems
			for (String file : extractedFiles) {
				// File fileFrom = new File(file);
				// InputStream fileStream = new FileInputStream(file);
				FileReader fileReader = new FileReader(file);
				BufferedReader fileBufferedReader = new BufferedReader(fileReader);
	
				Path fileTo = Paths.get(TXT_PATTERN.matcher(file).replaceAll("$1_thms$2"));
				//Path fileTo = Paths.get(file.replaceAll("([^.]*)(\\.txt)", "$1_thms$2"));
				
				List<String> thmList = ThmInput.readThm(fileBufferedReader, null, null);
				totalTextList.addAll(thmList);
				
				// write list of theorems to file
				Files.write(fileTo, thmList, Charset.forName("UTF-8"));
				
				fileReader.close();
				fileBufferedReader.close();
			}
			
			Path totalTxtPath = Paths.get(destBasePath + "total.txt");
			//less efficient than PrintWriter's!
			Files.write(totalTxtPath, totalTextList, Charset.forName("UTF-8"));
		}
	}
}
