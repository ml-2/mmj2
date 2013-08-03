//********************************************************************/
//* Copyright (C) 2005-2011                                          */
//* MEL O'CAT  X178G243 (at) yahoo (dot) com                         */
//* License terms: GNU General Public License Version 2              */
//*                or any later version                              */
//********************************************************************/
//*4567890123456 (71-character line to adjust editor window) 23456789*/


/**
 *  GMFFManager.java  0.01 11/01/2011
 *
 *  Version 0.01:
 *  Nov-01-2011: new.
 */

package mmj.gmff;

import  java.io.*;
import  java.util.*;
import  mmj.lang.Messages;
import  mmj.lang.Theorem;
import  mmj.mmio.MMIOConstants;
import  mmj.pa.ProofAsst;
import  mmj.pa.ProofAsstException;

/**
 *  Serves as a central data store for GMFF work in progress
 *  and as the primary interface for access to GMFF services.
 *  <p>
 *  One thing that is different about <code>GMFFManager</code>
 *  than other "manager" type classes in mmj2 is that the
 *  <code>GMFFManager</code> object is instantiated by the
 *  <code>LogicalSystemBoss</code> in the <code>util</code>
 *  package rather than the <code>GMFFBoss</code>. This is
 *  because Metamath $t Comment statement(s) are accumulated
 *  during the <code>LoadFile</code> process -- though not
 *  parsed at that time. Hence, a reference to the
 *  <code>GMFFManager</code> instance is stored in
 *  <code>LogicalSystem</code>.
 *  <p>
 *  Another thing that is different than typical mmj2
 *  processing is that the GMFF RunParms which establish
 *  settings for parameters are not validated when the
 *  RunParms are initially read. Nothing in GMFF happens
 *  until the first time the user -- or a command-style
 *  GMFF RunParm -- requests that GMFF typeset something.
 *  This functionality matches the way Metamath works and
 *  also saves users who have no interest in using GMFF
 *  from aggravation if there are errors in the GMFF-related
 *  inputs. A side-effect of delayed validation of GMFF
 *  RunParms is additional complexity. <code>GMFFManager</code>
 *  deals with that complexity in <code>initialization()</code>
 *  where all of the cached RunParms are validated and merged
 *  with default settings for parameters.
 */
public class GMFFManager {

	private boolean  gmffInitialized
	                            = false;

    private File     filePath;

    private Messages messages;

    private Map      symTbl     = null;

    private ArrayList<String> 		typesetDefinitionsCache;

    private int 					nbrTypesetDefinitionsProcessedSoFar
                                = 0;

    // via RunParmGMFFExportParms or wherever
    private ArrayList<GMFFExportParms>
    								inputGMFFExportParmsList;

    private ArrayList<GMFFUserTextEscapes>
    								inputGMFFUserTextEscapesList;

    private GMFFUserExportChoice    inputGMFFUserExportChoice;

	// contains defaults merged with inputGMFFExportParmsList
	// contains at most one entry for each exportType!
	private ArrayList<GMFFExportParms>
									exportParmsList;

	// list of exporters constructed from final contents of
	//  exportParmsList and userTextEscapesList
	private ArrayList       		gmffExporterList;

	// - contains defaults merged with inputGMFFUserTextEscapesList
	// - contains at most one entry for each exportType!
	// - list built using gmffExporterListas a key -- invalid
	//   if user text escapes list's export type not present in
	//   exportParmsList.
	private ArrayList<GMFFUserTextEscapes>
	 								userTextEscapesList;

	// loaded using cached Metamath typesetting defintion
	// comment records.
	private ArrayList<GMFFExporterTypesetDefs>
									exporterTypesetDefsList;

	// loaded with default merged with inputGMFFUserExportChoice
	private GMFFUserExportChoice gmffUserExportChoice;

	// loaded with Exporters from gmffExporterList according
	// to inputGMFFUserExportChoice
    private GMFFExporter[]  		selectedExporters;

	/**
	 *  Standard constructor.
	 *  <p>
	 *  Called by <code>LogicalSystemBoss</code> when the
	 *  first <code>LoadFile</code> RunParm is executed.
	 *  <p>
	 *  Sets up GMFF data structures but does not load
	 *  them. Sets <code>gmffInitialized</code> to <code>
	 *  false</code> to trigger initialization when the
	 *  first GMFF service request is received.
	 *  <p>
	 *  @param filePath path for building directories.
	 *  @param messages The Messages object.
	 */
    public GMFFManager(File     filePath,
                       Messages messages) {

		gmffInitialized         = false;

		this.filePath           = filePath;
        this.messages           = messages;

        typesetDefinitionsCache = new ArrayList<String>(1);

		nbrTypesetDefinitionsProcessedSoFar
		                        = 0;

        inputGMFFExportParmsList
        						=
        	new ArrayList<GMFFExportParms>(
					GMFFConstants.DEFAULT_EXPORT_PARMS.length);

        inputGMFFUserTextEscapesList
                                =
        	new ArrayList<GMFFUserTextEscapes>(
										GMFFConstants.DEFAULT_EXPORT_PARMS.length);
        inputGMFFUserExportChoice
                                = null;

		exporterTypesetDefsList =
        	new ArrayList<GMFFExporterTypesetDefs>(
					GMFFConstants.DEFAULT_EXPORT_PARMS.length);
    }

	/**
	 *  Calls <code>initialize()</code>, generates an audit report
	 *  of GMFF RunParms and default settings,mand if requested,
	 *  prints the typesetting definitions obtained from the input
	 *  Metamath file(s) $t Comment statements.
	 *  <p>
	 *  This function is called by GMFFBoss in response to a
	 *  <code>GMFFInitialize</code> RunParm.
	 *  <p>
	 *  @param printTypesettingDefinitions prints data from Metamath
	 *           $t Comments for which there are <code>GMFFExportParms</code>
	 *           with matching <code>typesetDefs</code> (we don't
	 *           load data from the $t's unless it is needed.)
	 */
	public void gmffInitialize(boolean printTypesettingDefinitions)
				throws GMFFException {

		initialization();

		generateInitializationAuditReport();

		if (printTypesettingDefinitions) {
 			generateTypesettingDefinitionsReport();
		}
	}

	/**
	 *  Caches parameters from one RunParm for later validation
	 *  and use.
	 *  <p>
	 *  Also invokes <code>forceReinitialization()</code> which
	 *  sets <code>gmffInitialized = false</code> to force
	 *  re-initialization of GMFF the next time a service request
	 *  is made.
	 *  <p>
	 *  This function is called by <code>GMFFBoss</code> in
	 *  response to a <code>GMFFExportParms</code> RunParm.
	 *  <p>
	 *  @param inputGMFFExportParms data from GMFFExportParms
	 *				RunParm.
	 */
    public void  accumInputGMFFExportParms(
						   GMFFExportParms inputGMFFExportParms) {

		inputGMFFExportParmsList.add(inputGMFFExportParms);
		forceReinitialization();
	}

	/**
	 *  Caches parameters from one RunParm for later validation
	 *  and use.
	 *  <p>
	 *  Also invokes <code>forceReinitialization()</code> which
	 *  sets <code>gmffInitialized = false</code> to force
	 *  re-initialization of GMFF the next time a service request
	 *  is made.
	 *  <p>
	 *  This function is called by <code>GMFFBoss</code> in
	 *  response to a <code>GMFFUserTextEscapes</code> RunParm.
	 *  <p>
	 *  @param inputGMFFUserTextEscapes data from GMFFUserTextEscapes
	 *				RunParm.
	 */
    public void accumInputGMFFUserTextEscapesList(
		                  GMFFUserTextEscapes inputGMFFUserTextEscapes) {

		inputGMFFUserTextEscapesList.add(inputGMFFUserTextEscapes);
		forceReinitialization();
	}

	/**
	 *  Caches one Metamath $t Comment statement for later validation
	 *  and use.
	 *  <p>
	 *  Also invokes <code>forceReinitialization()</code> which
	 *  sets <code>gmffInitialized = false</code> to force
	 *  re-initialization of GMFF the next time a service request
	 *  is made.
	 *  <p>
	 *  This function is called by <code>LogicalSystem</code> during
	 *  processing of a <code>LoadFile</code> RunParm.
	 *  <p>
	 *  @param comment String Metamath $t Comment statement as stored
	 *              in <code>SrcStmt</code> (the "$(" and "$)" delimiters
	 *              are removed at this pointand the first token is "$t").
	 */
	public void cacheTypesettingCommentForGMFF(String comment) {

		typesetDefinitionsCache.add(comment);
		forceReinitialization();
	}

	/**
	 *  Stores the contents of the <code>GMFFUserExportChoice</code>
	 *  from one RunParm for later validation and use.
	 *  <p>
	 *  Also invokes <code>forceReinitialization()</code> which
	 *  sets <code>gmffInitialized = false</code> to force
	 *  re-initialization of GMFF the next time a service request
	 *  is made.
	 *  <p>
	 *  This function is called by <code>GMFFBoss</code> in
	 *  response to a <code>GMFFUserExportChoice</code> RunParm.
	 *  <p>
	 *  @param choice from GMFFUserExportChoice RunParm.
	 */
	public void setInputGMFFUserExportChoice(GMFFUserExportChoice choice) {
		this.inputGMFFUserExportChoice
		                        = choice;
		forceReinitialization();
	}


	/**
	 *  Sets the <code>symTbl</code> for use in generating GMFF
	 *  exports.
	 *  <p>
	 *  This function is called by the <code>LogicalSystem</code>
	 *  constructor -- which itself is excuted during processing
	 *  of the first <code>LoadFile</code> RunParm. <code>symTbl</code>
	 *  is itself constructed during construction of <code>LogicalSystem</code>
	 *  so this function is necessary even though <code>GMFFManager</code>
	 *  is passed as an argument to the <code>LogicalSystem</code>
	 *  constructor (a somewhat circular arrangement.)
     *
	 *  <code>symTbl</code> is needed because an error message is
	 *  generated when a symbol to be typeset is not found in the
	 *  Metamath $t definitions, but only if the symbol string is
	 *  really a valid symbol (and is not a <code>WorkVar</code>.)
	 *  (GMFF not not require that Proof Worksheets be valid, just
	 *  that the Proof Worksheet format is loosely followed.)
	 *  <p>
	 *  @param symTbl The Symbol Table Map from <code>LogicalSystem</code>
	 */
	public void setSymTbl(Map symTbl) {
		this.symTbl             = symTbl;
	}

	/**
	 *  Gets the <code>symTbl</code> for use in generating GMFF
	 *  exports.
	 *  <p>
	 *  @return The Symbol Table Map, <code>symTbl</code> from
	 *   			<code>LogicalSystem</code>
	 */
	public Map getSymTbl() {
		return symTbl;
	}

	/**
	 *  Gets the <code>messages</code> object.
	 *  <p>
	 *  @return The Messages object used to store error and
	 *          informational messages during mmj2 processing.
	 */
	public Messages getMessages() {
		return messages;
	}

	/**
	 *  Returns the <code>gmffInitialized</code> boolean variable.
	 *  <p>
	 *  @return true if GMFF already initialized, otherwise false.
	 */
	public boolean isGMFFInitialized() {
		return gmffInitialized;
	}

	/**
	 *  Forces GMFF to re-initialize itself the next time a service
	 *  request is received by settting <code>gmffInitialized</code>
	 *  to <code>false</code>.
	 */
	public void forceReinitialization() {
		gmffInitialized         = false;
	}

	/**
	 *  Exports one or a range of Proof Worksheets of a given
	 *  file type from a designated directory.
	 *  <p>
	 *  This function implements the <code>GMFFExportFromFolder</code>
	 *  RunParm command.
	 *  <p>
	 *  The sort sequence used to select and output Proof Worksheets
	 *  is File Name minus File Type.
	 *  <p>
	 *  Refer to mmj2\doc\gmffdoc\C:\mmj2jar\GMFFDoc\GMFFRunParms.txt
	 *  for more info about the parameters on the
	 *  <code>GMFFExportFromFolder</code>  RunParm.
     *  <p>
     *  @param inputDirectory The Directory to export Proof Worksheets
     *  				      from
     *  @param theoremLabelOrAsterisk Either a theorem label or "*".
     *                        If theorem label input then that is used
     *                        as the starting point, otherwise processing
     *                        begins at the first file in the directory.
     *  @param inputFileType  File Type to select, including the "."
     *                        (e.g. ".mmp")
     *  @param maxNumberToExport Limits the number of exports processed.
     *                        Must be greater than zero and less then
     *                        2 billionish...
     *  @param appendFileNameIn Specifies an append-mode file name to which
     *                        all of the exported proofs will be written --
     *                        within the folder specified for each Export
     *                        Type on the GMFFExportParms RunParm; overrides
     *                        the normal name assigned to an export file.
     *  @throws GMFFException is errors encountered.
	 */
	public void exportFromFolder(
					String inputDirectory,
					String theoremLabelOrAsterisk,
					String inputFileType,
					String maxNumberToExport,
					String appendFileNameIn)
						throws GMFFException {

		String confirmationMessage;

		if (!gmffInitialized) {
			initialization();
		}

		String fileType         =
			validateFileType(
				inputFileType);

		String labelOrAsterisk  =
			validateTheoremLabelOrAsterisk(
				theoremLabelOrAsterisk);

        String appendFileName   =
            validateAppendFileName(
				appendFileNameIn);

		int max             	=
				validateMaxNumberToExport(
					maxNumberToExport);

		GMFFFolder inputFolder  =
			new GMFFFolder(filePath,
			               inputDirectory,
						   " ");

		if (labelOrAsterisk.equals(GMFFConstants.OPTION_VALUE_ALL)
			  ||
			max > 1) {

			String lowestNamePrefix;
			if (labelOrAsterisk.equals(GMFFConstants.OPTION_VALUE_ALL)) {
				lowestNamePrefix
				                = new String("");
			}
			else {
				lowestNamePrefix
				                = labelOrAsterisk;
			}

			File[] fileArray    =
				inputFolder.listFiles(fileType,
									  lowestNamePrefix);

			if (fileArray.length == 0) {
				messages.accumErrorMessage(
					GMFFConstants.ERRMSG_NO_PROOF_FILES_SELECTED_ERROR_1
					+ inputDirectory
					+ GMFFConstants.ERRMSG_NO_PROOF_FILES_SELECTED_ERROR_2
					+ fileType);
				return;
			}

			for (int i = 0; i < fileArray.length && i < max; i++) {

				String proofWorksheetText
									=
					GMFFInputFile.
						getFileContents(
							inputFolder,
							fileArray[i],
							" ",
							GMFFConstants.PROOF_WORKSHEET_MESSAGE_DESCRIPTOR,
							GMFFConstants.PROOF_WORKSHEET_BUFFER_SIZE);

				confirmationMessage
				                =
					exportProofWorksheet(proofWorksheetText,
										 appendFileName);
				if (confirmationMessage != null) {
					messages.accumInfoMessage(
						confirmationMessage);
				}
			}
			return;
		}

		String proofWorksheetText
							=
			GMFFInputFile.
				getFileContents(
					inputFolder,
					(labelOrAsterisk + fileType),
					" ",
					GMFFConstants.PROOF_WORKSHEET_MESSAGE_DESCRIPTOR,
					GMFFConstants.PROOF_WORKSHEET_BUFFER_SIZE);

		confirmationMessage     =
			exportProofWorksheet(proofWorksheetText,
			                     appendFileName);
		if (confirmationMessage != null) {
			messages.accumInfoMessage(
				confirmationMessage);
		}

	}

	/**
	 *  Exports one theorem or a range of theorems from
	 *  <code>LogicalSystem</code>.
	 *  <p>
	 *  This function implements the <code>GMFFExportTheorem</code>
	 *  RunParm command.
	 *  <p>
	 *  The sort sequence used to select and output thereoms
	 *  is <code>MObj.seq</code> -- that is, order of appearance
	 *  in the <code>LogicalSystem</code>.
	 *  <p>
	 *  Refer to mmj2\doc\gmffdoc\C:\mmj2jar\GMFFDoc\GMFFRunParms.txt
	 *  for more info about the parameters on the
	 *  <code>GMFFExportThereom</code> RunParm.
     *  <p>
     *  @param theoremLabelOrAsterisk Either a theorem label or "*".
     *                        If theorem label input then that is used
     *                        as the starting point, otherwise processing
     *                        begins at the first file in the directory.
     *  @param maxNumberToExport Limits the number of exports processed.
     *                        Must be greater than zero and less then
     *                        2 billionish...
     *  @param appendFileNameIn Specifies an append-mode file name to which
     *                        all of the exported proofs will be written --
     *                        within the folder specified for each Export
     *                        Type on the GMFFExportParms RunParm; overrides
     *                        the normal name assigned to an export file.
     *  @param proofAsst      The <code>ProofAsst</code> object, used to
     *                        format Proof Worksheets from Metamath (RPN)
     *                        proofs.
     *  @throws GMFFException is errors encountered.
	 */
	public void exportTheorem(
					String 		theoremLabelOrAsterisk,
					String 		maxNumberToExport,
					String 		appendFileNameIn,
					ProofAsst 	proofAsst)
						throws GMFFException {

		if (!gmffInitialized) {
			initialization();
		}

		String labelOrAsterisk  =
			validateTheoremLabelOrAsterisk(
				theoremLabelOrAsterisk);

        String appendFileName   =
            validateAppendFileName(
				appendFileNameIn);

		int max             	=
				validateMaxNumberToExport(
					maxNumberToExport);

		if (labelOrAsterisk.equals(GMFFConstants.OPTION_VALUE_ALL)
			  ||
			max > 1) {

			String startTheorem;
			if (labelOrAsterisk.
					equals(
						GMFFConstants.OPTION_VALUE_ALL)) {
				startTheorem    = null;
			}
			else {
				startTheorem    = labelOrAsterisk;
			}

			Iterator iterator;
			try {
				iterator		=
					proofAsst.
						getSortedSkipSeqTheoremIterator(
							startTheorem);
			}
			catch (ProofAsstException e) {
				messages.accumErrorMessage(
					e.getMessage());
				return;
			}

			if (!iterator.hasNext()) {
				messages.accumErrorMessage(
					GMFFConstants.ERRMSG_NO_THEOREMS_SELECTED_ERROR_1
						+ labelOrAsterisk);
				return;
			}
			int i               = 0;
			do {
				gmffExportOneTheorem(
					(Theorem)iterator.next(),
					appendFileName,
					proofAsst);

			} while (++i < max &&
			         iterator.hasNext());
		}
		else {
			gmffExportOneTheorem(
				labelOrAsterisk,
			  	appendFileName,
				proofAsst);
		}

	}

	/**
	 *  Exports one <code>Theorem</code> from the
	 *  <code>LogicalSystem</code>.
	 *  loaded
	 *  <p>
	 *  This function is called by other functions in
	 *  <code>GMFFManager</code> but it would be perfectly
	 *  valid to call it externally.
	 *  <p>
	 *  This function calls <code>ProofAsst.exportOneTheorem</code>
	 *  which creates a Proof Worksheet from a Metamath (RPN) proof.
	 *  If the theorem's proof is incomplete or invalid, or if it
	 *  contains no assertions, an error message results (and if
	 *  input argument <code>theorem</code> is null an
	 *  <code>IllegalArgumentException</code> will result ;-)
	 *  <p>
     *  @param theorem        <code>Theorem</code> to be exported.
     *  @param appendFileName Specifies an append-mode file name to which
     *                        exported proof will be written --
     *                        within the folder specified for each Export
     *                        Type on the GMFFExportParms RunParm; overrides
     *                        the normal name assigned to an export file.
     *  @param proofAsst      The <code>ProofAsst</code> object, used to
     *                        format Proof Worksheets from Metamath (RPN)
     *                        proofs.
     *  @throws GMFFException is errors encountered.
	 */
	public void gmffExportOneTheorem(
					Theorem   theorem,
	                String    appendFileName,
	                ProofAsst proofAsst)
	                				throws GMFFException {

		if (!gmffInitialized) {
			initialization();
		}

		String proofWorksheetText;
		try {
			proofWorksheetText  =
				proofAsst.exportOneTheorem(theorem);
		}
		catch (IllegalArgumentException e) {
			messages.accumErrorMessage(
				GMFFConstants.ERRMSG_GMFF_THEOREM_EXPORT_PA_ERROR_1
				+ theorem.getLabel()
				+ GMFFConstants.ERRMSG_GMFF_THEOREM_EXPORT_PA_ERROR_2
				+ e.getMessage());
			return;
		}

		if (proofWorksheetText == null) {
			messages.accumErrorMessage(
				GMFFConstants.ERRMSG_GMFF_THEOREM_EXPORT_PA_ERROR_1
				+ theorem.getLabel());
		}
		else {
			String confirmationMessage
			                    =
				exportProofWorksheet(proofWorksheetText,
									 appendFileName);

			if (confirmationMessage != null) {
				messages.accumInfoMessage(
					confirmationMessage);
			}
		}
	}

	/**
	 *  Exports one <code>Theorem</code> from the
	 *  <code>LogicalSystem</code>.
	 *  loaded
	 *  <p>
	 *  This function is called by other functions in
	 *  <code>GMFFManager</code> but it would be perfectly
	 *  valid to call it externally.
	 *  <p>
	 *  This function calls <code>ProofAsst.exportOneTheorem</code>
	 *  which creates a Proof Worksheet from a Metamath (RPN) proof.
	 *  If the theorem's proof is incomplete or invalid, or if it
	 *  contains no assertions, an error message results -- and if
	 *  input argument <code>theoremLabel</code> is null or
	 *  invalid an exception is thrown...
	 *  <p>
     *  @param theoremLabel   label of <code>Theorem</code> to be exported.
     *  @param appendFileName Specifies an append-mode file name to which
     *                        exported proof will be written --
     *                        within the folder specified for each Export
     *                        Type on the GMFFExportParms RunParm; overrides
     *                        the normal name assigned to an export file.
     *  @param proofAsst      The <code>ProofAsst</code> object, used to
     *                        format Proof Worksheets from Metamath (RPN)
     *                        proofs.
     *  @throws GMFFException is errors encountered.
	 */
	public void gmffExportOneTheorem(
					String    theoremLabel,
	                String    appendFileName,
	                ProofAsst proofAsst)
	                	throws GMFFException {

		if (!gmffInitialized) {
			initialization();
		}

		String proofWorksheetText;
		try {
			proofWorksheetText  =
				proofAsst.exportOneTheorem(theoremLabel);
		}
		catch (IllegalArgumentException e) {
			messages.accumErrorMessage(
				GMFFConstants.ERRMSG_GMFF_THEOREM_EXPORT_PA_ERROR_1
				+ theoremLabel
				+ GMFFConstants.ERRMSG_GMFF_THEOREM_EXPORT_PA_ERROR_2
				+ e.getMessage());
			return;
		}

		if (proofWorksheetText == null) {
			messages.accumErrorMessage(
				GMFFConstants.ERRMSG_GMFF_THEOREM_EXPORT_PA_ERROR_1
				+ theoremLabel);
		}
		else {
			String confirmationMessage
			                    =
				exportProofWorksheet(proofWorksheetText,
									 appendFileName);

			if (confirmationMessage != null) {
				messages.accumInfoMessage(
					confirmationMessage);
			}
		}
	}

	/**
	 *  Exports a single Proof Worksheet to files in the requested
	 *  formats.
	 *  <p>
	 *  This function is called by <code>ProofAsst</code> and by
	 *  various functions in <code>GMFFManager</code>.
	 *  <p>
	 *  The following functions are performed:
	 *  <p>
	 *  <ol>
	 *  <li> Initializes GMFF if necessary.
	 *  <li> Throws an exception if the parameter settings
	 *       do not include at least one active export format.
	 *  <li> Loads the input proofText into a
	 *       new <code>ProofWorksheetCache</code> object
	 *  <li> invokes each active export request passing the
	 *       <code>ProofWorksheetCache</code> and accumulating
	 *       confirmation messages from them in return.
	 *  <li> returns the accumulated confirmation messages to
	 *       the caller.
	 *  </ol>
	 *  <p>
	 *  @param proofText String containing text in the format
	 *           of an mmj2 Proof Worksheet.
	 *  @param appendFileName name of a file to which export
	 *           data should be appended (in the proper directory
	 *           for the Export Type), or <code>null</code> if
	 *           GMFF is supposed to generate the name.
	 *  @return String containing confirmation messages about
	 *           successful export(s) if no errors occurred.
	 *  @throws GMFFException if error found.
	 */
    public String exportProofWorksheet(String proofText,
                                       String appendFileName)
            	throws GMFFException {

		StringBuffer confirmationMessage
		                        = new StringBuffer();

		if (!gmffInitialized) {
			initialization();
		}

		if (selectedExporters.length == 0) {
			throw new GMFFException(
				GMFFConstants.
					ERRMSG_NO_EXPORT_TYPES_SELECTED_ERROR_1);
		}

		ProofWorksheetCache p   =
			new ProofWorksheetCache(
				proofText);

		for (int i = 0; i < selectedExporters.length; i++) {
			String confirm      =
				selectedExporters[i].
					exportProofWorksheet(
						p,
						appendFileName);
			if (confirm != null) {
				confirmationMessage.append(confirm);
			}
		}

		return confirmationMessage.toString();
	}

   /**
    *  Implements RunParm GMFFParseMMTypesetDefsComment.
    *  <p>
    *  This function is primarily used for testing. It
    *  parses a file containing a single Metamath comment
    *  statement -- of the $t variety. Because it is
    *  intended for standalone use in testing, it does
    *  not require GMFF initialization prior to use, and
    *  it does not check for or trigger GMFF initialization.
    *  <p>
    *  The code is quick and dirty because it is just used
    *  for testing. Efficiency not an issue.
    *  <p>
    *  @param typesetDefKeyword The Metamath $t keyword
    *			to select for parsing (e.g. "htmldef")
    *  @param myDirectory The directory containing the Metamath
    *           .mm file to parse.
    *  @param myMetamathTypesetCommentFileName File Name in
    *           myDirectory to parse.
    *  @param runParmPrintOption if true prints the input, including
    *           the directory, file name, typesetDefKeyword and
    *           the entire Metamath file.
    *  @throws GMFFException if errors found.
    */
	public void parseMetamathTypesetComment(
					String  typesetDefKeyword,
					String  myDirectory,
					String  myMetamathTypesetCommentFileName,
					boolean runParmPrintOption)
						throws GMFFException {

		GMFFFolder myFolder     =
			new GMFFFolder(filePath,
			               myDirectory,
						   typesetDefKeyword);

		String mmDollarTComment =
			GMFFInputFile.
				getFileContents(
					myFolder,
					myMetamathTypesetCommentFileName,
					typesetDefKeyword,
					GMFFConstants.METAMATH_DOLLAR_T_MESSAGE_DESCRIPTOR,
					GMFFConstants.METAMATH_DOLLAR_T_BUFFER_SIZE);

		if (runParmPrintOption) {
			messages.accumInfoMessage(
				GMFFConstants.ERRMSG_INPUT_DOLLAR_T_COMMENT_MM_FILE_1
				+ myFolder.getAbsolutePath()
				+ GMFFConstants.ERRMSG_INPUT_DOLLAR_T_COMMENT_MM_FILE_2
				+ myMetamathTypesetCommentFileName
				+ GMFFConstants.ERRMSG_INPUT_DOLLAR_T_COMMENT_MM_FILE_3
				+ typesetDefKeyword
				+ GMFFConstants.ERRMSG_INPUT_DOLLAR_T_COMMENT_MM_FILE_4
				+ mmDollarTComment
				+ GMFFConstants.ERRMSG_INPUT_DOLLAR_T_COMMENT_MM_FILE_5);
		}

		mmDollarTComment        =
			stripMetamathCommentDelimiters(
				mmDollarTComment);

		GMFFExporterTypesetDefs myTypesetDefs
		                        =
        	new GMFFExporterTypesetDefs(
					typesetDefKeyword,
        	        GMFFConstants.METAMATH_DOLLAR_T_MAP_SIZE);

		ArrayList<GMFFExporterTypesetDefs> list
		                        =
            new ArrayList<GMFFExporterTypesetDefs>(1);

        list.add(myTypesetDefs);

		TypesetDefCommentParser parser
		                        =
			new TypesetDefCommentParser(list,
			                            messages);

		parser.doIt(mmDollarTComment);

		myTypesetDefs.printTypesetDefs(messages);
	}

	/**
	 *  Generates and outputs to the Messages object an audit
	 *  report of the final results of GMFF initialization
	 *  showing the parameters and settings in use.
	 */
	public void generateInitializationAuditReport() {
		StringBuffer sb         = new StringBuffer();
		sb.append(MMIOConstants.NEW_LINE_CHAR);
		sb.append(GMFFConstants.INITIALIZATION_AUDIT_REPORT_1);
		sb.append(MMIOConstants.NEW_LINE_CHAR);
		sb.append(MMIOConstants.NEW_LINE_CHAR);

     	sb.append(GMFFConstants.INITIALIZATION_AUDIT_REPORT_2_UC_1);
		sb.append(gmffUserExportChoice.generateAuditReportText());
		sb.append(MMIOConstants.NEW_LINE_CHAR);
		sb.append(MMIOConstants.NEW_LINE_CHAR);

		for (int i = 0; i < selectedExporters.length; i++) {

			sb.append(GMFFConstants.INITIALIZATION_AUDIT_REPORT_3_SE_1);
			sb.append(i);
			sb.append(GMFFConstants.INITIALIZATION_AUDIT_REPORT_3_SE_2);
		    sb.append(MMIOConstants.NEW_LINE_CHAR);
		    sb.append(MMIOConstants.NEW_LINE_CHAR);

			sb.append(GMFFConstants.INITIALIZATION_AUDIT_REPORT_4_EP_1);
			sb.append(selectedExporters[i].gmffExportParms.generateAuditReportText());
		    sb.append(MMIOConstants.NEW_LINE_CHAR);
		    sb.append(MMIOConstants.NEW_LINE_CHAR);

			sb.append(GMFFConstants.INITIALIZATION_AUDIT_REPORT_5_TE_1);
			sb.append(selectedExporters[i].gmffUserTextEscapes.generateAuditReportText());
		    sb.append(MMIOConstants.NEW_LINE_CHAR);
		    sb.append(MMIOConstants.NEW_LINE_CHAR);

			sb.append(GMFFConstants.INITIALIZATION_AUDIT_REPORT_6_TD_1);
			sb.append(selectedExporters[i].gmffExporterTypesetDefs.generateAuditReportText());
		    sb.append(MMIOConstants.NEW_LINE_CHAR);
		    sb.append(MMIOConstants.NEW_LINE_CHAR);
		}

		messages.accumInfoMessage(sb.toString());
	}

	/**
	 *  Generates and outputs to the Messages object an audit
	 *  report of the Metamath $t typesetting definitions
	 *  after parsing of the input Metamath file.
	 */
	public void generateTypesettingDefinitionsReport() {

		for (GMFFExporterTypesetDefs t: exporterTypesetDefsList) {

			t.printTypesetDefs(messages);

		}
	}

	/**
	 *  Initializes GMFF using all cached RunParms, default
	 *  settings and cached Metamath $t Comment statements.
	 *  <p>
	 *  This was surprisingly tricky to get right due to
	 *  the interrelated nature of the cached data, some
	 *  of which may be redundant and/or updates to previous
	 *  inputs. The key data element is Export Type (e.g.
	 *  "html", "althtml", "latex", etc.) -- it is the
	 *  key used to match and merge the primary inputs.
	 *  For example, two GMFFExportParms RunParms with the
	 *  same Export Type result in one output Exporter
	 *  (export request), with the last input RunParm
	 *  overriding the previous inputs.
	 *  <p>
	 *  Functions performed, in order:
	 *  <ol>
	 *  <li>set <code>gmffInitialized = false</code>
	 *      (it will only be set to <code>true</code>
	 *      if this entire gauntlet of logic is completed
	 *      without thrown exceptions.
	 *  <li>build consolidated list of Export Parms using
	 *      cached input and default settings.
	 *  <li>build consolidated list of User Text Escapes using
	 *      cached input and default settings.
	 *  <li>build list of enabled, valid <code>Exporter</code>s.
	 *  <li>load the Metamath Typeset Def list and update
	 *      the <code>Exporter</code>s to point to the defs.
	 *  <li>validate and load the User Export Choice (either
	 *      a particular Export Type or "ALL")
	 *  <li>load final list of Selected (chosen)
	 *      <code>Exporter</code>s.
	 *  <li>set <code>gmffInitialized = true</code>
	 *  </ol>
	 */
	private void initialization()
				throws GMFFException {
		gmffInitialized         = false;

		exportParmsList     	= loadExportParmsList();

		userTextEscapesList     = loadUserTextEscapesList();

		gmffExporterList        = loadExporterList();

		updateExporterTypesetDefsList();

		parseMetamathTypesetDefCache();

		gmffUserExportChoice    = loadGMFFUserExportChoice();

		selectedExporters		= loadSelectedExportersArray();

	    gmffInitialized         = true;
	}

   /**
    *  Builds a list containing the default export
    *  parms with input user export parms merged
    *  on top.
    *  <p>
    *  Validates the ExportParms after building the
    *  consolidated list.
    */
	private ArrayList<GMFFExportParms> loadExportParmsList()
					throws GMFFException {

		ArrayList<GMFFExportParms> listOut
		                        =
			new ArrayList<GMFFExportParms>(
					GMFFConstants.DEFAULT_EXPORT_PARMS.length);

		// load defaults:
		for (int i = 0;
			 i < GMFFConstants.DEFAULT_EXPORT_PARMS.length;
			 i++) {
			updateExportParmsList(listOut,
			        			  GMFFConstants.DEFAULT_EXPORT_PARMS[i]);
		}

		// merge in user input export parms
		for (GMFFExportParms p: inputGMFFExportParmsList) {
			updateExportParmsList(listOut,
			                 	  p);
		}

		validateExportParmsList(listOut);

		return listOut;
	}

	private void updateExportParmsList(
							ArrayList<GMFFExportParms> listOut,
	                        GMFFExportParms            t) {

		int j                   = listOut.indexOf(t);
		if (j == -1) {
			listOut.add(t);
		}
		else {
			listOut.set(j,
			            t);
		}
	}



   /**
    *  Builds a list containing the default text escapes
    *  with user input text escapes merged on top.
    *  <p>
    *  Validates the text escapes after building the
    *  consolidated list.
    */
	private ArrayList<GMFFUserTextEscapes>
				loadUserTextEscapesList()
					throws GMFFException {

		ArrayList<GMFFUserTextEscapes> listOut
		                        =
			new ArrayList<GMFFUserTextEscapes>(
				GMFFConstants.DEFAULT_USER_TEXT_ESCAPES.length);

		// load defaults:
		for (int i = 0;
			 i < GMFFConstants.DEFAULT_USER_TEXT_ESCAPES.length;
			 i++) {
			updateUserTextEscapesList(
				listOut,
			    GMFFConstants.DEFAULT_USER_TEXT_ESCAPES[i]);
		}

		// merge in user input export parms
		for (GMFFUserTextEscapes u: inputGMFFUserTextEscapesList) {
			updateUserTextEscapesList(listOut,
			        		u);
		}

		validateUserTextEscapesList(listOut);

		return listOut;
	}

	private void updateUserTextEscapesList(
						ArrayList<GMFFUserTextEscapes> listOut,
	                    GMFFUserTextEscapes            t) {

		int j                   = listOut.indexOf(t);
		if (j == -1) {
			listOut.add(t);
		}
		else {
			listOut.set(j,
			            t);
		}
	}

	private void validateExportParmsList(
					ArrayList<GMFFExportParms> list)
						throws GMFFException {
		boolean validationErrors
		                        = false;

		for (GMFFExportParms p: list) {
			if (!p.areExportParmsValid(filePath,
			                           messages)) {
				validationErrors
				                = true;
			}
		}

		if (validationErrors) {
			throw new GMFFException(
				GMFFConstants.ERRMSG_EXPORT_PARMS_LIST_ERROR_1);
		}
	}

	private void validateUserTextEscapesList(
					ArrayList<GMFFUserTextEscapes> list)
						throws GMFFException {
		boolean validationErrors
		                        = false;

		for (GMFFUserTextEscapes u: list) {
			if (!u.areUserTextEscapesValid(exportParmsList,
										   messages)) {
				validationErrors
				                = true;
			}
		}

		if (validationErrors) {
			throw new GMFFException(
				GMFFConstants.ERRMSG_USER_TEXT_ESCAPES_LIST_ERROR_1);
		}
	}


	private ArrayList loadExporterList()
					throws GMFFException {

		ArrayList x             =
			new ArrayList(
				exportParmsList.size());

		GMFFUserTextEscapes t;
		for (GMFFExportParms p: exportParmsList) {

			if (p.onoff.compareToIgnoreCase(GMFFConstants.EXPORT_PARM_ON)
			    != 0) {
				continue;
			}
			t 					= null;
			for (GMFFUserTextEscapes u: userTextEscapesList) {
				if (u.exportType.equals(p.exportType)) {
					t 			= u;
					break;
				}
			}
			GMFFExporter e      =
				GMFFExporter.ConstructModelExporter(this,
			                                        p,
							                        t);
			x.add(e);
		}
		return x;
	}

	private void updateExporterTypesetDefsList() {

		GMFFExporter exporter;

		exporterLoop: for (int i = 0;
						   i < gmffExporterList.size();
						   i++) {
			exporter            =
				((GMFFExporter)gmffExporterList.get(i));

			for (GMFFExporterTypesetDefs t: exporterTypesetDefsList) {

				if (exporter.gmffExportParms.typesetDefKeyword.equals(
					       t.typesetDefKeyword)) {

					exporter.gmffExporterTypesetDefs
				                = t;

					continue exporterLoop;
				}
			}

			// ergo, not found in gmffExporterTypesetDefsList...
			GMFFExporterTypesetDefs t
								=
				new GMFFExporterTypesetDefs(
					exporter.gmffExportParms.typesetDefKeyword,
					symTbl.size());

			exporter.gmffExporterTypesetDefs
						= t;

			updateExporterTypesetDefsList(t);

		}
	}

	private void updateExporterTypesetDefsList(
					GMFFExporterTypesetDefs newT) {

		for (GMFFExporterTypesetDefs oldT: exporterTypesetDefsList) {

			if (oldT.typesetDefKeyword.equals(
				newT.typesetDefKeyword)) {
				return;
			}
		}
		exporterTypesetDefsList.add(newT);
	}

	private void parseMetamathTypesetDefCache()
					throws GMFFException {

		TypesetDefCommentParser parser
								=
			new TypesetDefCommentParser(exporterTypesetDefsList,
			                            messages);

		String typesetDefComment;

		while (nbrTypesetDefinitionsProcessedSoFar <
		       typesetDefinitionsCache.size()) {

			typesetDefComment 	=
				typesetDefinitionsCache.get(
					nbrTypesetDefinitionsProcessedSoFar);

			parser.doIt(typesetDefComment);

			++nbrTypesetDefinitionsProcessedSoFar;
		}
	}

	private GMFFUserExportChoice loadGMFFUserExportChoice()
						throws GMFFException {

		GMFFUserExportChoice userExportChoice;

		if (inputGMFFUserExportChoice == null) {
			userExportChoice    = GMFFConstants.
										DEFAULT_USER_EXPORT_CHOICE;
		}
		else {
			userExportChoice    = inputGMFFUserExportChoice;
		}

		validateUserExportChoice(userExportChoice);

		return userExportChoice;
	}

	private void validateUserExportChoice(
			GMFFUserExportChoice userExportChoice)
							throws GMFFException {

		userExportChoice.validateUserExportChoice(exportParmsList);
	}

	private GMFFExporter[] loadSelectedExportersArray() {

		int nbrSelected         = 0;

		if (gmffUserExportChoice.exportTypeOrAll.
				compareToIgnoreCase(GMFFConstants.USER_EXPORT_CHOICE_ALL)
			== 0) {

			nbrSelected         = gmffExporterList.size();
		}
		else {
			Iterator iterator   = gmffExporterList.iterator();
			while (iterator.hasNext()) {

				GMFFExporter e  = (GMFFExporter)iterator.next();

				if (e.gmffExportParms.exportType.equals(
						gmffUserExportChoice.exportTypeOrAll)) {

					++nbrSelected;
				}
			}
		}

		GMFFExporter[] selected = new GMFFExporter[nbrSelected];

		if (nbrSelected > 0) {

			int i               = 0;

			Iterator iterator   = gmffExporterList.iterator();
			while (iterator.hasNext()) {

				GMFFExporter exporter
								= (GMFFExporter)iterator.next();

				if ((gmffUserExportChoice.exportTypeOrAll.
						compareToIgnoreCase(
							GMFFConstants.USER_EXPORT_CHOICE_ALL)
					== 0)

					||

					gmffUserExportChoice.exportTypeOrAll.equals(
				    	exporter.gmffExportParms.exportType)) {

					selected[i++]
					            = exporter;
				}
			}
		}

		return selected;
	}

	private String validateFileType(String fileType)
				throws GMFFException {

		if (!GMFFExportParms.isPresentWithNoWhitespace(fileType)
		        ||
		    fileType.charAt(0) != GMFFConstants.FILE_TYPE_DOT) {

			throw new GMFFException(
				GMFFConstants.ERRMSG_FILE_TYPE_BAD_MISSING_1
				+ fileType);
		}
		return fileType;
	}

	private int validateMaxNumberToExport(String max)
					throws GMFFException {

		if (GMFFExportParms.isPresentWithNoWhitespace(max)) {
        	Integer i = null;
			try {
				i = Integer.valueOf(max.trim());
				if (i > 0) {
					return i.intValue();
				}
			}
			catch(NumberFormatException e) {
			}
		}
		throw new GMFFException(
			GMFFConstants.ERRMSG_MAX_NBR_TO_EXPORT_BAD_MISSING_1
			+ max);
	}

    private String validateAppendFileName(String appendFileNameIn)
    						throws GMFFException {
		String appendFileName;

		if (appendFileNameIn == null) {
			return null;
		}

		appendFileName          = appendFileNameIn.trim();
		if (appendFileName.length() == 0) {
			return null;
		}

		if (GMFFExportParms.isPresentWithNoWhitespace(appendFileName)
		    &&
		    appendFileName.indexOf(
				GMFFConstants.APPEND_FILE_NAME_ERR_CHAR_1) == -1
		    &&
		    appendFileName.indexOf(
				GMFFConstants.APPEND_FILE_NAME_ERR_CHAR_2) == -1
		    &&
		    appendFileName.indexOf(
				GMFFConstants.APPEND_FILE_NAME_ERR_CHAR_3) == -1) {
		}
		else {
			throw new GMFFException(
				GMFFConstants.ERRMSG_APPEND_FILE_NAME_ERROR_1
				+ appendFileName);
		}

		return appendFileName;
	}


	private String validateTheoremLabelOrAsterisk(
						String theoremLabelOrAsterisk)
				throws GMFFException {
		if (!GMFFExportParms.
				isPresentWithNoWhitespace(
					theoremLabelOrAsterisk)) {
			throw new GMFFException(
				GMFFConstants.ERRMSG_LABEL_OR_ASTERISK_BAD_MISSING_1
				+ theoremLabelOrAsterisk);
		}
		return theoremLabelOrAsterisk;
	}

	// removes $( and $) delimiters to match the way Systemizer
	// caches SrcStmt $t comments. this code is for testing so
	// we just brutally abort w/out a fancy message or more
	// careful inspection of the input.
	private String stripMetamathCommentDelimiters(String mmComment)
				throws GMFFException {

		int startC                 =
			mmComment.indexOf(MMIOConstants.MM_START_COMMENT_KEYWORD);

		int endC                   =
			mmComment.lastIndexOf(MMIOConstants.MM_END_COMMENT_KEYWORD);

		if (startC == -1 ||
		    endC   == -1 ||
		    startC > endC) {
			throw new GMFFException(
				GMFFConstants.
					ERRMSG_INVALID_METAMATH_TYPESET_COMMENT_ERROR_1);
		}

		return mmComment.substring(
					startC +
						MMIOConstants.MM_START_COMMENT_KEYWORD.length(),
					endC);
	}
}