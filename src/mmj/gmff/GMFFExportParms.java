//********************************************************************/
//* Copyright (C) 2011                                               */
//* MEL O'CAT  X178G243 (at) yahoo (dot) com                         */
//* License terms: GNU General Public License Version 2              */
//*                or any later version                              */
//********************************************************************/
//*4567890123456 (71-character line to adjust editor window) 23456789*/

/**
 *  GMFFExportParms.java  0.01 11/01/2011
 *
 *  Version 0.01:
 *  Nov-01-2011: new.
 */

package mmj.gmff;

import  java.util.*;
import  java.io.*;
import  java.nio.charset.*;
import  mmj.lang.Messages;
import  mmj.util.UtilConstants;

/**
 *  GMFFExportParms holds the parameters from a single
 *  RunParm of the same name plus a <code>File</code>
 *  object for building relative paths.
 *  <p>
 *  It is basically just a data structure with some
 *  attached utility functions on the data elements.
 *  <p>
 *  During validation however, the <code>GMFFFolders</code>
 *  for the exportDirectory and modelsDirectory are
 *  instantiated and saved for later use.
 *  <p>
 *  The reason for creating this class is that GMFF
 *  parameter type RunParms are not validated and
 *  processed until GMFF is initialized, typically
 *  when the user requests an export. So the RunParms
 *  are cached until initialization time.
 *  <p>
 *  The GMFFExportParms are keyed by exportType.
 */
public class GMFFExportParms
                                implements Comparable {

    public final 	String  	exportType;
	public       	String  	onoff;
	public       	String  	typesetDefKeyword;
	public       	String  	exportDirectory;
	public       	String  	exportFileType;
	public       	String  	modelsDirectory;
	public       	String  	modelId;
	public       	String  	charsetEncoding;
	public       	String  	outputFileName;

	public 			GMFFFolder	exportFolder;
	public 			GMFFFolder	modelsFolder;

    /**
     *  A GMFF utility to confirm that a given string is not
     *  null or empty, and that it contains no whitespace.
     *  <p>
     *  @param s The string to be validated.
     *  @return true if valid, otherwise false.
     */
	public static boolean isPresentWithNoWhitespace(String s) {
		if (s          == null ||
		    s.length() == 0) {
			return false;
		}

		for (int i = 0; i < s.length(); i++) {
			if (Character.isWhitespace(s.charAt(i))) {
				return false;
			}
		}

		return true;
	}

    /**
     *  A constructor to build a GMFFExportParms object without
     *  validating any of the parameters.
     *  <p>
     *  See mmj2\doc\GMFFDoc\GMFFRunParms.txt
     *
     *  @param exportType Export Type (e.g. "html" or "althtml")
     *  @param onoff      OnOff ("ON" or "OFF")
     *  @param typesetDefKeyword Metamath $t comment keyword
     *                    (e.g. "htmldef" or "althtmldef")
     *  @param exportDirectory where output files written
     *  @param exportFileType File Type including the period
     *                    (e.g. ".html")
     *  @param modelsDirectory Directory where GMFF Models are
     *                    stored for this Export Type
     *  @param modelId    Model Id for this GMFFExportParms instance.
     *  @param charsetEncoding Charset Encoding Name (see doc).
     *  @param outputFileName Output File Name minus the file type;
     *                    if omitted file name composed using theorem
     *                    label.
     *  <p>
     */
    public GMFFExportParms(
		         	String  exportType,
	             	String  onoff,
	             	String  typesetDefKeyword,
	             	String  exportDirectory,
	             	String  exportFileType,
	             	String  modelsDirectory,
	             	String  modelId,
	             	String  charsetEncoding,
	             	String  outputFileName) {

		this.exportType         = exportType;
		if (onoff != null) {
			this.onoff          = onoff.trim().toUpperCase();
		}
		this.typesetDefKeyword  = typesetDefKeyword;
	    this.exportDirectory    = exportDirectory;
	    this.exportFileType     = exportFileType;
	    this.modelsDirectory    = modelsDirectory;
	    this.modelId            = modelId;
	    this.charsetEncoding    = charsetEncoding;

		if (outputFileName != null
		    &&
		    outputFileName.length() != 0) {

			this.outputFileName = outputFileName.trim();
		}

    }

    /**
     *  Converts to Audit Report string for testing
     *  purposes.
     *  <p>
     *  @return String containing the relevant fields.
     */
    public String generateAuditReportText() {
		String s                = new String(
			UtilConstants.RUNPARM_GMFF_EXPORT_PARMS
			+ GMFFConstants.AUDIT_REPORT_COMMA + exportType
	        + GMFFConstants.AUDIT_REPORT_COMMA + onoff
	        + GMFFConstants.AUDIT_REPORT_COMMA + typesetDefKeyword
	        + GMFFConstants.AUDIT_REPORT_COMMA + exportDirectory
	        + GMFFConstants.AUDIT_REPORT_COMMA + exportFileType
	        + GMFFConstants.AUDIT_REPORT_COMMA + modelsDirectory
	        + GMFFConstants.AUDIT_REPORT_COMMA + modelId
	        + GMFFConstants.AUDIT_REPORT_COMMA + charsetEncoding
	        + GMFFConstants.AUDIT_REPORT_COMMA + outputFileName);
	    return s;

    }

    /**
     *  Validates Export Parms data.
     *  <p>
     *  For ease of use, validation does not stop at the
     *  first error found. Any errors are accumulated in
     *  the Messages object.
     *  <p>
     *  However, if <code>onoff</code> set to "OFF" the
     *  following parameters are not validated.
     *  <p>
     *  @param filePath path for building directories.
     *             May be null, absolute or relative.
     *  @param messages The Messages object.
     *  @return true if valid otherwise false.
     */
	public boolean areExportParmsValid(File     filePath,
	                                   Messages messages) {

		boolean errorsFound     = false;
		try {
			validateExportType();
		}
		catch (GMFFException e) {
			errorsFound         = true;
			messages.accumErrorMessage(e.getMessage());
		}

		try {
			validateOnOff();
		}
		catch (GMFFException e) {
			errorsFound         = true;
			messages.accumErrorMessage(e.getMessage());
		}

		//stop validation if this set of export parms disabled!
		if (onoff.compareToIgnoreCase(GMFFConstants.EXPORT_PARM_OFF)
			== 0) {
			return !errorsFound;
		}

		try {
			validateTypesetDefKeyword();
		}
		catch (GMFFException e) {
			errorsFound         = true;
			messages.accumErrorMessage(e.getMessage());
		}

		try {
			exportFolder        =
				validateExportDirectory(
					filePath);
		}
		catch (GMFFException e) {
			errorsFound         = true;
			messages.accumErrorMessage(e.getMessage());
		}

		try {
			validateExportFileType();
		}
		catch (GMFFException e) {
			errorsFound         = true;
			messages.accumErrorMessage(e.getMessage());
		}

		try {
			modelsFolder        =
				validateModelsDirectory(
					filePath);
		}
		catch (GMFFException e) {
			errorsFound         = true;
			messages.accumErrorMessage(e.getMessage());
		}

		try {
			validateModelId();
		}
		catch (GMFFException e) {
			errorsFound         = true;
			messages.accumErrorMessage(e.getMessage());
		}

		try {
			validateCharsetEncoding();
		}
		catch (GMFFException e) {
			errorsFound         = true;
			messages.accumErrorMessage(e.getMessage());
		}

		try {
			validateOutputFileName();
		}
		catch (GMFFException e) {
			errorsFound         = true;
			messages.accumErrorMessage(e.getMessage());
		}

		return !errorsFound;
	}

    /**
     *  Validates <code>exportType</code>.
     *  <p>
     *  <ul>
     *  <li>Not null or empty string
     *  <li>Must contain no whitespace
     *  </ul>
     *  @throws GMFFException if error found.
     */
	public void validateExportType()
						throws GMFFException {
		if (!GMFFExportParms.isPresentWithNoWhitespace(exportType)) {
			throw new GMFFException(
				GMFFConstants.ERRMSG_EXPORT_TYPE_BAD_MISSING_1
				+ exportType);
		}
	}

    /**
     *  Validates <code>onOff</code>.
     *  <p>
     *  <ul>
     *  <li>Not null or empty string
     *  <li>Must contain no whitespace
     *  <li>equal to ON or OFF
     *  </ul>
     *  @throws GMFFException if error found.
     */
	public void validateOnOff()
						throws GMFFException {

		if (GMFFExportParms.isPresentWithNoWhitespace(onoff)
		    &&
		    (onoff.compareToIgnoreCase(GMFFConstants.EXPORT_PARM_ON)
		        == 0)
	    	||
		    (onoff.compareToIgnoreCase(GMFFConstants.EXPORT_PARM_OFF)
		        == 0)) {
		}
		else {
			throw new GMFFException(
				GMFFConstants.ERRMSG_ON_OFF_BAD_MISSING_1
				+ exportType
				+ GMFFConstants.ERRMSG_ON_OFF_BAD_MISSING_2
				+ onoff);
		}
	}

    /**
     *  Validates <code>typesetDefKeyword</code>.
     *  <p>
     *  <ul>
     *  <li>Not null or empty string
     *  <li>Must contain no whitespace
     *  </ul>
     *  @throws GMFFException if error found.
     */
	public void validateTypesetDefKeyword()
						throws GMFFException {

		if (!GMFFExportParms.
				isPresentWithNoWhitespace(
					typesetDefKeyword)) {
			throw new GMFFException(
				GMFFConstants.ERRMSG_TYPESET_DEF_KEYWORD_BAD_MISSING_1
				+ exportType
				+ GMFFConstants.ERRMSG_TYPESET_DEF_KEYWORD_BAD_MISSING_2
				+ typesetDefKeyword);
		}
	}

    /**
     *  Validates <code>exportDirectory</code>.
     *  <p>
     *  <ul>
     *  <li>Not null or empty string
     *  <li>Must contain no whitespace
     *  <li>Must be able to create <code>GMFFFolder</code>
     *      using <code>exportDirectory</code> parameter.
     *  </ul>
     *  @param  filePath path for building directory. May
     *              be null, absolute or relative.
     *  @return GMFFFolder for Export Directory parameter.
     *  @throws GMFFException if error found.
     */
	public GMFFFolder validateExportDirectory(File filePath)
						throws GMFFException {

		GMFFFolder folder;
		if (!GMFFExportParms.
				isPresentWithNoWhitespace(
					exportDirectory)) {
			throw new GMFFException(
				GMFFConstants.ERRMSG_EXPORT_DIRECTORY_BAD_MISSING_1
				+ exportType
				+ GMFFConstants.ERRMSG_EXPORT_DIRECTORY_BAD_MISSING_2
				+ exportDirectory);
		}

		try {
			folder			    =
				new GMFFFolder(
					filePath,
					exportDirectory,
		            exportType);
		}
		catch (GMFFException e) {
			throw new GMFFException(
				GMFFConstants.ERRMSG_EXPORT_DIRECTORY_BAD2_1
				+ exportType
				+ GMFFConstants.ERRMSG_EXPORT_DIRECTORY_BAD2_2
				+ e.getMessage());
		}

		return folder;
	}

    /**
     *  Validates <code>exportFileType</code>.
     *  <p>
     *  <ul>
     *  <li>Not null or empty string
     *  <li>Must contain no whitespace
     *  <li>Must begin with "."
     *  </ul>
     *  @throws GMFFException if error found.
     */
	public void validateExportFileType()
						throws GMFFException {

		if (!GMFFExportParms.isPresentWithNoWhitespace(exportFileType)
		    ||
		    exportFileType.charAt(0) != GMFFConstants.FILE_TYPE_DOT) {

			throw new GMFFException(
				GMFFConstants.ERRMSG_EXPORT_FILE_TYPE_BAD_MISSING_1
				+ exportType
				+ GMFFConstants.ERRMSG_EXPORT_FILE_TYPE_BAD_MISSING_2
				+ exportFileType);
		}
	}

    /**
     *  Validates <code>modelstDirectory</code>.
     *  <p>
     *  <ul>
     *  <li>Not null or empty string
     *  <li>Must contain no whitespace
     *  <li>Must be able to create <code>GMFFFolder</code>
     *      using <code>modelsDirectory</code> parameter.
     *  </ul>
     *  @param  filePath path for building directory. May
     *              be null, absolute or relative.
     *  @return Models Folder.
     *  @throws GMFFException if error found.
     */
	public GMFFFolder validateModelsDirectory(File filePath)
						throws GMFFException {

		GMFFFolder folder;
		if (!GMFFExportParms.
				isPresentWithNoWhitespace(
					modelsDirectory)) {
			throw new GMFFException(
				GMFFConstants.ERRMSG_MODELS_DIRECTORY_BAD_MISSING_1
				+ exportType
				+ GMFFConstants.ERRMSG_MODELS_DIRECTORY_BAD_MISSING_2
				+ modelsDirectory);
		}

		try {
			folder   			=
				new GMFFFolder(
					filePath,
					modelsDirectory,
		            exportType);
		}
		catch (GMFFException e) {
			throw new GMFFException(
				GMFFConstants.ERRMSG_MODELS_DIRECTORY_BAD2_1
				+ exportType
				+ GMFFConstants.ERRMSG_MODELS_DIRECTORY_BAD2_2
				+ e.getMessage());
		}
		return folder;
	}

    /**
     *  Validates <code>modelId</code>.
     *  <p>
     *  <ul>
     *  <li>Not null or empty string
     *  <li>Must equal "A" :-)
     *  </ul>
     *  @throws GMFFException if error found.
     */
	public void validateModelId()
						throws GMFFException {

		if (!GMFFExportParms.isPresentWithNoWhitespace(modelId)
		    ||
		    !modelId.equals(GMFFConstants.MODEL_A)) {
			throw new GMFFException(
				GMFFConstants.ERRMSG_MODEL_ID_BAD_MISSING_1
				+ exportType
				+ GMFFConstants.ERRMSG_MODEL_ID_BAD_MISSING_2
				+ modelId);
		}
	}

    /**
     *  Validates <code>charsetEncoding</code>.
     *  <p>
     *  <ul>
     *  <li>Not null or empty string
     *  <li><code>Charset.isSupported(charsetEncoding) == true</code>
     *  </ul>
     *  @throws GMFFException if error found.
     */
	public void validateCharsetEncoding()
						throws GMFFException {

		if (!GMFFExportParms.
				isPresentWithNoWhitespace(
					charsetEncoding)) {
			throw new GMFFException(
				GMFFConstants.ERRMSG_CHARSET_ENCODING_BAD_MISSING_1
				+ exportType
				+ GMFFConstants.ERRMSG_CHARSET_ENCODING_BAD_MISSING_2
				+ charsetEncoding);
		}

        boolean isSupported;
        try {
            isSupported       =
                Charset.isSupported(charsetEncoding);
        }
        catch(IllegalCharsetNameException e) {
            throw new GMFFException(
                GMFFConstants.ERRMSG_CHARSET_ENCODING_INVALID_1
                + exportType
                + GMFFConstants.ERRMSG_CHARSET_ENCODING_INVALID_2
                + charsetEncoding
                + GMFFConstants.ERRMSG_CHARSET_ENCODING_INVALID_3
                + e.getMessage());
        }

        if (!isSupported) {
            throw new GMFFException(
                GMFFConstants.
                		ERRMSG_CHARSET_ENCODING_UNSUPPORTED_1
                + exportType
                + GMFFConstants.
                		ERRMSG_CHARSET_ENCODING_UNSUPPORTED_2
                + charsetEncoding
                + GMFFConstants.
                		ERRMSG_CHARSET_ENCODING_UNSUPPORTED_3);
        }
	}

    /**
     *  Validates <code>outputFileName</code>.
     *  <p>
     *  <ul>
     *  <li>Not null or empty string
     *  <li>Must contain no whitespace
     *  <li>Must not contain "/", "\" or ":"
     *  </ul>
     *  @throws GMFFException if error found.
     */
    public void validateOutputFileName()
    						throws GMFFException {

		if ((outputFileName == null)
		    ||

		    (GMFFExportParms.isPresentWithNoWhitespace(outputFileName)
		     &&
		     outputFileName.indexOf(
				GMFFConstants.OUTPUT_FILE_NAME_ERR_CHAR_1) == -1
		     &&
		     outputFileName.indexOf(
				GMFFConstants.OUTPUT_FILE_NAME_ERR_CHAR_2) == -1
		     &&
		     outputFileName.indexOf(
				GMFFConstants.OUTPUT_FILE_NAME_ERR_CHAR_3) == -1)) {
		}
		else {
			throw new GMFFException(
				GMFFConstants.ERRMSG_OUTPUT_FILE_NAME_ERROR_1
				+ exportType
				+ GMFFConstants.ERRMSG_OUTPUT_FILE_NAME_ERROR_2
				+ outputFileName);
		}

		return;
	}



    /**
     *  converts to String
     *
     *  @return returns GMFFExportParms.exportType string;
     */
    public String toString() {
        return exportType;
    }

    /**
     * Computes hashcode for this GMFFExportParms
     *
     * @return hashcode for the GMFFExportParms
     *        (GMFFExportParms.exportType.hashcode())
     */
    public int hashCode() {
        return exportType.hashCode();
    }

    /**
     * Compare for equality with another GMFFExportParms.
     * <p>
     * Equal if and only if the GMFFExportParms exportType
     * strings are equal
     * and the obj to be compared to this object is not null
     * and is a GMFFExportParms as well.
     * <p>
     * @param obj another GMFFExportParms -- otherwise will return false.
     *
     * @return returns true if equal, otherwise false.
     */
    public boolean equals(Object obj) {
        return (this == obj) ? true
                : !(obj instanceof GMFFExportParms) ? false
                        : exportType.equals(
							((GMFFExportParms)obj).exportType);
    }

    /**
     * Compares GMFFExportParms object based on the
     * primary key, exportType.
     *
     * @param obj GMFFExportParms object to compare to this GMFFExportParms
     *
     * @return returns negative, zero, or a positive int
     * if this GMFFExportParms object is less than, equal to
     * or greater than the input parameter obj.
     *
     */
    public int compareTo(Object obj) {
        return exportType.compareTo(
				((GMFFExportParms)obj).exportType);
    }

    /**
     *  EXPORT_TYPE sequences by GMFFExportParms.exportType.
     */
    static public final Comparator EXPORT_TYPE
            = new Comparator() {
        public int compare(Object o1, Object o2) {
            return (((GMFFExportParms)o1).exportType.compareTo(
					((GMFFExportParms)o2).exportType));
        }
    };
}