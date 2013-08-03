//********************************************************************/
//* Copyright (C) 2005-2011                                          */
//* MEL O'CAT  X178G243 (at) yahoo (dot) com                         */
//* License terms: GNU General Public License Version 2              */
//*                or any later version                              */
//********************************************************************/
//*4567890123456 (71-character line to adjust editor window) 23456789*/


/**
 *  MinProofStepStmt.java  0.01 11/01/2011
 *
 *  Version 0.01:
 *  Nov-01-2011: new.
 */

package mmj.gmff;

/**
 *  General object representing a proof step statement in a
 *  MinProofWorksheet.
 *  <p>
 *  The term "proof step" in Proof Worksheet terminology
 *  is any Proof Worksheet statement that contains a formula --
 *  these are <code>MinHypothesisStep</code> and
 *  <code>MinDerivationStep</code>, the latter includes the
 *  final step, the "qed" step.
 */
 public class MinProofStepStmt extends MinProofWorkStmt {


    /**
     *  Standard MinProofStepStmt constructor.
     *  <p>
     *  @param w the <code>MinProofworksheet</code> object which
     *				contains the proof step.
     *  @param slc Array of proof step lines each comprised
     *				of an Array of <code>String</code>s
     *              called "chunks", which may be either Metamath
     *              whitespace or Metamath tokens. Hence the
     *              acronym "slc" refers to Statement Line Chunks.
     */
    public MinProofStepStmt(MinProofWorksheet w,
                            String[][]        slc) {

        super(w,
              slc);
    }

    /**
     *  Formats export data for the proof step statement
     *  according to the <code>Model A</code> specifications
     *  and loads the data into a specified buffer.
     *  <p>
     *  Model A model files for <code>MinProofStepStmt</code> objects
     *  are "mandatory", meaning that if any of the model files are
     *  not found, the export process is halted and an error message
     *  is generated.
     *  <p>
     *  Export of a proof step involves formatting two things: the
     *  part of each line prior to the first formula symbol on that
     *  line, and the typesetting and formatting of each formula symbol.
     *  <p>
     *  We go to a lot of trouble to format the pre-formula part of
     *  the line because it is vital to preserve the indentation
     *  before the formula symbols -- even though the process of
     *  generating typeset formulas will necessarily alter the
     *  indentations of the formula's symbols.
     *  <p>
     *  Note the quirky handling of <code>modelAStep1X</code> and
     *  <code>modelAStep3X</code>. If one of these model files is
     *  empty then no data is output at that location. This quirky
     *  feature will generally not be used but is provided for
     *  maximum flexibility in creating export format models.
     *  <p>
     *  Additional information may be found \GMFFDoc\GMFFModels.txt.
     *  <p>
     *  @param gmffExporter The <code>GMFFExporter</code> requesting
     *				the export data build.
     *  @param exportBuffer The <code>StringBuffer</code> to which
     *               exported data is to be output.
  	 *  @throws GMFFException if errors are encountered during the
  	 *               export process.
     */
	public void buildModelAExport(
						 	GMFFExporter gmffExporter,
		               	 	StringBuffer exportBuffer)
				            	throws GMFFException {

		String modelAStep1X     =
			gmffExporter.getMandatoryModelFile(
				GMFFConstants.MODEL_A_STEP1X_NAME,
				w.getTheoremLabel());

		String modelAStep3X     =
			gmffExporter.getMandatoryModelFile(
				GMFFConstants.MODEL_A_STEP3X_NAME,
				w.getTheoremLabel());

		String[] lineChunks;
		String   chunk;

		for (int i = 0; i < stmtLineChunks.length; i++) {
			lineChunks          = stmtLineChunks[i];

			gmffExporter.appendMandatoryModelFile(
				exportBuffer,
				GMFFConstants.MODEL_A_STEP0_NAME,
				w.getTheoremLabel());

			int startOfFormulaSymbols
			                    = -1;
			int nbrNonWhitespaceTokensFound
			                    = 0;
			int j;
			for (j = 0; j < lineChunks.length; j++) {
				chunk           = lineChunks[j];
				if (chunk.length() > 0 &&
				    !isChunkWhitespace(chunk)) {

					++nbrNonWhitespaceTokensFound;
					if (i == 0 &&
						nbrNonWhitespaceTokensFound < 2) {
						continue;
					}
					startOfFormulaSymbols
							    = j;
					break;
				}
			}

			String linePart1    =
				getCleanedLineString(i,
				                     0,
				                     j);

			if (modelAStep1X.length() > 0) {
				gmffExporter.escapeAndAppendProofText(
					exportBuffer,
					linePart1);
			}

			gmffExporter.appendMandatoryModelFile(
				exportBuffer,
				GMFFConstants.MODEL_A_STEP2_NAME,
				w.getTheoremLabel());

			if (startOfFormulaSymbols != -1 &&
				modelAStep3X.length() > 0) {

				typesetFormulaSymbols(gmffExporter,
		               	 			  exportBuffer,
									  lineChunks,
				            		  startOfFormulaSymbols);
			}

			gmffExporter.appendMandatoryModelFile(
				exportBuffer,
				GMFFConstants.MODEL_A_STEP4_NAME,
				w.getTheoremLabel());
		}
	}
}