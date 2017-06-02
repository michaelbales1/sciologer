package bales;
//=======================================================================================
//bundleKMZ.java takes a specified kml file and bundles it with:
//1. a ground overlay
//2. a set of files (models, drawings, photos, or icons) in a specified directory
//It zips all of these elements into a kmz file that can in turn be explored in Google Earth
//Adapted from http://www.devx.com/tips/Tip/14049
//=======================================================================================
//
import java.io.*;

import bales.File_Settings;

import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.Enumeration;

import org.apache.logging.log4j.Logger; 
import org.apache.logging.log4j.LogManager;

public class bundleKMZ 
{
	private static final Logger logger = LogManager.getLogger(bundleKMZ.class.getName());

	public static void main(String args[]) {
		logger.info("Launching KMZ bundler...");
		logger.info("===============================================================");
		logger.info("===============================================================");
		if (args.length != 4) {
			logger.error("Usage: cmd kmlFile dirToZip iconType outFile");
		}
		String kmlFile=args[0];
		String dirToZip=args[1];
		String iconType=args[2];
		String outFile=args[3];
		//Get the name of the file or directory to compress.
		String fileName = dirToZip;
//		String outFile=args[3].substring(0,fileName.length()-4);
		//Use the makeZip method to create a Zip archive.
		try {
			makeZip(kmlFile, fileName, outFile);
		}
		// Print out any errors we encounter.
		catch (Exception e) {
			logger.error(e);
		}
	} // end main

	/**
	 * Compresses a file or directory into a Zip archive. Users of the
	 * class supply the name of the file or directory as an argument.
	 */
	private static ZipOutputStream zos;
	/**
	 * Creates a Zip archive. If the name of the file passed in is a
	 * directory, the directory's contents will be made into a Zip file.
	 */
	public static void makeZip(String kmlFile, String fileName, String outFile)
	throws IOException, FileNotFoundException
	{
		// logger.debug("KMLFile: " + kmlFile);
		// logger.debug("File: " + fileName);
		// logger.debug("outFile: " + outFile);

		File kfile = new File(kmlFile);
		File file = new File(fileName);
//		File file = new File(fileName.substring(0,fileName.length()-4));
		//	 zos = new ZipOutputStream(new FileOutputStream(file + ".kmz"));
		zos = new ZipOutputStream(new FileOutputStream(outFile));
		addKML(kfile);
		//Call recursion.
		recurseFiles(file);
		//We are done adding entries to the zip archive,
		//so close the Zip output stream.
		zos.close();
	}

	private static void addKML(File file)
	throws IOException, FileNotFoundException
	{
		byte[] buf = new byte[1024];
		int len;
		//Create a new Zip entry with the file's name.
		//	 String pathToDir = "c:\\e\\o";
		String pathToDir = File_Settings.outputPath;
		//	 ZipEntry zipEntry = new ZipEntry(file.getPath().substring(pathToDir.length()));
		ZipEntry zipEntry = new ZipEntry(file.getPath().substring(pathToDir.length()));
		//	 System.out.println("Adding " + file.getName());
		//	 ZipEntry zipEntry = new ZipEntry(file.toString());
		//Create a buffered input stream out of the file
		//we're trying to add into the Zip archive.
		FileInputStream fin = new FileInputStream(file);
		BufferedInputStream in = new BufferedInputStream(fin);
		zos.putNextEntry(zipEntry);
		//Read bytes from the file and write into the Zip archive.
		while ((len = in.read(buf)) >= 0) {
			zos.write(buf, 0, len);
		}
		//Close the input stream.
		in.close();
		//Close this entry in the Zip stream.
		zos.closeEntry();
	}


	/**
	 * Recurses down a directory and its subdirectories to look for
	 * files to add to the Zip. If the current file being looked at
	 * is not a directory, the method adds it to the Zip file.
	 */
	private static void recurseFiles(File file)
	throws IOException, FileNotFoundException
	{
		if (file.isDirectory()) {
			//Create an array with all of the files and subdirectories
			//of the current directory.
			String[] fileNames = file.list();
			if (fileNames != null) {
				//Recursively add each array entry to make sure that we get
				//subdirectories as well as normal files in the directory.
				for (int i=0; i<fileNames.length; i++) {
					recurseFiles(new File(file, fileNames[i]));
				}
			}
		}
		//Otherwise, a file so add it as an entry to the Zip file.
		else {
			byte[] buf = new byte[1024];
			int len;
			//Create a new Zip entry with the file's name.
			//	 String pathToDir = "c:\\e\\k\\";
			String pathToDir = File_Settings.kmzPath;
			ZipEntry zipEntry = new ZipEntry(file.getPath().substring(pathToDir.length()));
			//	 System.out.println("Adding " + file.getName());
			//	 ZipEntry zipEntry = new ZipEntry(file.toString());
			//Create a buffered input stream out of the file
			//we're trying to add into the Zip archive.
			FileInputStream fin = new FileInputStream(file);
			BufferedInputStream in = new BufferedInputStream(fin);
			zos.putNextEntry(zipEntry);
			//Read bytes from the file and write into the Zip archive.
			while ((len = in.read(buf)) >= 0) {
				zos.write(buf, 0, len);
			}
			//Close the input stream.
			in.close();
			//Close this entry in the Zip stream.
			zos.closeEntry();
		}
	}


	private static void zipDir(String zipFileName, String dir)
	{
		File dirObj = new File(dir);
		if(!dirObj.isDirectory())
		{
			logger.error(dir + " is not a directory");
			//System.exit(1);
		}

		try
		{
			logger.debug("Writing zip file to " + zipFileName);
			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFileName));

			logger.info("Creating : " + zipFileName);

			addDir(dirObj, out);
			// Complete the ZIP file
			out.close();


		}
		catch (IOException e)
		{
			e.printStackTrace();
			logger.error(e.getMessage());
		}

	}

	private static void addDir(File dirObj, ZipOutputStream out) throws IOException
	{
		File[] files = dirObj.listFiles();
		byte[] tmpBuf = new byte[1024];
		for (int i=0; i<files.length; i++)
		{
			if(files[i].isDirectory())
			{
				addDir(files[i], out);
				continue;
			}
			FileInputStream in = new FileInputStream(files[i].getAbsolutePath());
			//	 System.out.println(" Adding: " + files[i].getPath());
			//out.putNextEntry(new ZipEntry(files.getAbsolutePath()));
			out.putNextEntry(new ZipEntry(files[i].getPath()));
			// Transfer from the file to the ZIP file
			int len;
			while((len = in.read(tmpBuf)) > 0)
			{
				out.write(tmpBuf, 0, len);
			}
			// Complete the entry
			out.closeEntry();
			in.close();
		}
	}
} // End class