package mmj.transforms;

import java.util.*;

import mmj.lang.*;
import mmj.pa.ProofStepStmt;

/** This class could be used for equivalence transformations */
public class EquivalenceInfo {
    private boolean dbg;

    /** This field is true if this object was initialized */
    private boolean isInit = false;

    /** The map from type to corresponding equivalence operators */
    private Map<Cnst, Stmt> eqMap;

    /** The list of commutative rules for equivalence operators: A = B => B = A */
    private Map<Stmt, Assrt> eqCommutatives;

    /**
     * The list of transitive rules for equivalence operators:
     * <p>
     * A = B & B = C => A = C
     */
    private Map<Stmt, Assrt> eqTransitivies;

    private TrOutput output;

    public EquivalenceInfo() {}

    // ------------------------------------------------------------------------
    // ------------------------Initialization----------------------------------
    // ------------------------------------------------------------------------

    public void initMe(final List<Assrt> assrtList, final TrOutput output,
        final boolean dbg)
    {
        this.output = output;
        this.dbg = dbg;
        isInit = true;

        eqCommutatives = new HashMap<Stmt, Assrt>();
        for (final Assrt assrt : assrtList)
            findEquivalenceCommutativeRules(assrt);

        eqTransitivies = new HashMap<Stmt, Assrt>();
        for (final Assrt assrt : assrtList)
            findEquivalenceTransitiveRules(assrt);

        filterOnlyEqRules();
    }

    /**
     * Find commutative equivalence rules, like A = B => B = A
     * <p>
     * 
     * @param assrt the candidate
     */
    protected void findEquivalenceCommutativeRules(final Assrt assrt) {
        final VarHyp[] varHypArray = assrt.getMandVarHypArray();
        final LogHyp[] logHyps = assrt.getLogHypArray();
        final ParseTree assrtTree = assrt.getExprParseTree();

        if (logHyps.length != 1)
            return;

        final ParseTree hypTree = logHyps[0].getExprParseTree();

        if (varHypArray.length != 2)
            return;

        if (hypTree.getMaxDepth() != 2)
            return;

        if (assrtTree.getMaxDepth() != 2)
            return;

        if (assrtTree.getRoot().getChild().length != 2)
            return;

        final Stmt stmt = assrtTree.getRoot().getStmt();

        if (hypTree.getRoot().getStmt() != stmt)
            return;

        if (hypTree.getRoot().getChild()[0].getStmt() != assrtTree.getRoot()
            .getChild()[1].getStmt())
            return;

        if (hypTree.getRoot().getChild()[1].getStmt() != assrtTree.getRoot()
            .getChild()[0].getStmt())
            return;

        output.dbgMessage(dbg, "I-DBG Equivalence commutative assrt: %s: %s",
            assrt, assrt.getFormula());

        if (!eqCommutatives.containsKey(stmt))
            eqCommutatives.put(stmt, assrt);
    }

    /**
     * Find transitive equivalence rules, like A = B & B = C => A = C
     * <p>
     * 
     * @param assrt the candidate
     */
    protected void findEquivalenceTransitiveRules(final Assrt assrt) {
        final VarHyp[] mandVarHypArray = assrt.getMandVarHypArray();
        final LogHyp[] logHyps = assrt.getLogHypArray();
        final ParseTree assrtTree = assrt.getExprParseTree();

        if (logHyps.length != 2)
            return;

        final ParseTree hypTree1 = logHyps[0].getExprParseTree();
        final ParseTree hypTree2 = logHyps[1].getExprParseTree();

        if (mandVarHypArray.length != 2)
            return;

        if (hypTree1.getMaxDepth() != 2 || hypTree2.getMaxDepth() != 2)
            return;

        if (assrtTree.getMaxDepth() != 2)
            return;

        if (assrtTree.getRoot().getChild().length != 2)
            return;

        final Stmt stmt = assrtTree.getRoot().getStmt();

        if (hypTree1.getRoot().getStmt() != stmt)
            return;

        if (hypTree2.getRoot().getStmt() != stmt)
            return;

        // check for 'A' in 'A = B & B = C => A = C'
        if (hypTree1.getRoot().getChild()[0].getStmt() != assrtTree.getRoot()
            .getChild()[0].getStmt())
            return;

        // check for 'B' in 'A = B & B = C'
        if (hypTree1.getRoot().getChild()[1].getStmt() != hypTree2.getRoot()
            .getChild()[0].getStmt())
            return;

        // check for 'C' in 'A = B & B = C => A = C'
        if (hypTree2.getRoot().getChild()[1].getStmt() != assrtTree.getRoot()
            .getChild()[1].getStmt())
            return;

        output.dbgMessage(dbg, "I-DBG Equivalence transitive assrt: %s: %s",
            assrt, assrt.getFormula());
        if (!eqTransitivies.containsKey(stmt))
            eqTransitivies.put(stmt, assrt);
    }

    /**
     * We found candidates for equivalence from commutative and transitive
     * sides. Now compare results and remove unsuitable!
     */
    protected void filterOnlyEqRules() {
        while (true) {
            boolean changed = false;

            for (final Stmt eq : eqTransitivies.keySet())
                if (!eqCommutatives.containsKey(eq)) {
                    eqTransitivies.remove(eq);
                    changed = true;
                    break;
                }

            for (final Stmt eq : eqCommutatives.keySet())
                if (!eqTransitivies.containsKey(eq)) {
                    eqCommutatives.remove(eq);
                    changed = true;
                    break;
                }

            if (!changed)
                break;
        }

        // Debug output:
        for (final Stmt eq : eqTransitivies.keySet())
            output.dbgMessage(dbg, "I-DBG Equivalence rules: %s: %s and %s",
                eq, eqCommutatives.get(eq).getFormula(), eqTransitivies.get(eq)
                    .getFormula());

        // Create the reverse map:
        eqMap = new HashMap<Cnst, Stmt>();

        for (final Stmt eq : eqCommutatives.keySet()) {
            final Assrt assrt = eqCommutatives.get(eq);

            final ParseTree assrtTree = assrt.getExprParseTree();
            final Cnst type = assrtTree.getRoot().getChild()[0].getStmt()
                .getTyp();
            eqMap.put(type, eq);

            output.dbgMessage(dbg, "I-DBG Type equivalence map: %s: %s", type,
                eq);
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------Transformations---------------------------------
    // ------------------------------------------------------------------------

    /**
     * Creates equivalence node (like a = b )
     * 
     * @param left the left node
     * @param right the right node
     * @return the equivalence node
     */
    public ParseNode createEqNode(final ParseNode left, final ParseNode right) {
        final Cnst type = left.getStmt().getTyp();
        assert type == right.getStmt().getTyp();
        final Stmt equalStmt = getEqStmt(type);
        assert equalStmt != null;

        final ParseNode res = TrUtil.createBinaryNode(equalStmt, left, right);
        return res;
    }

    /**
     * Creates reverse step for another equivalence step (like b = a for a = b)
     * 
     * @param info the work sheet info
     * @param source the source (like a = b)
     * @return the equivalence node
     */
    public ProofStepStmt createReverse(final WorksheetInfo info,
        final ProofStepStmt source)
    {
        final ParseNode root = source.formulaParseTree.getRoot();
        final Stmt equalStmt = root.getStmt();
        final ParseNode left = root.getChild()[0];
        final ParseNode right = root.getChild()[1];
        final Assrt eqComm = getEqCommutative(equalStmt);

        assert eqComm != null;

        final ParseNode revNode = TrUtil.createBinaryNode(equalStmt, right,
            left);

        final ProofStepStmt res = info.getOrCreateProofStepStmt(revNode,
            new ProofStepStmt[]{source}, eqComm);
        return res;
    }

    /**
     * This function creates transitive inference for two steps (= is the
     * example of equivalence operator).
     * 
     * @param info the work sheet info
     * @param first the first statement ( a = b )
     * @param second the second statement ( b = c )
     * @return the result statement ( a = c )
     */
    public ProofStepStmt getTransitiveStep(final WorksheetInfo info,
        final ProofStepStmt first, final ProofStepStmt second)
    {
        if (first == null)
            return second;

        final ParseNode firstRoot = first.formulaParseTree.getRoot();
        final ParseNode secondRoot = second.formulaParseTree.getRoot();
        final Stmt equalStmt = firstRoot.getStmt();

        assert equalStmt == secondRoot.getStmt();
        assert firstRoot.getChild()[1].isDeepDup(secondRoot.getChild()[0]);

        final Assrt transitive = getEqTransitive(equalStmt);

        final ParseNode transitiveNode = TrUtil.createBinaryNode(equalStmt,
            firstRoot.getChild()[0], secondRoot.getChild()[1]);

        final ProofStepStmt resStmt = info.getOrCreateProofStepStmt(
            transitiveNode, new ProofStepStmt[]{first, second}, transitive);

        return resStmt;
    }

    public boolean isEquivalence(final Stmt stmt) {
        return eqCommutatives.containsKey(stmt);
    }

    public boolean isInit() {
        return isInit;
    }

    public Stmt getEqStmt(final Cnst type) {
        return eqMap.get(type);
    }

    public Assrt getEqCommutative(final Stmt stmt) {
        return eqCommutatives.get(stmt);
    }

    public Assrt getEqTransitive(final Stmt stmt) {
        return eqTransitivies.get(stmt);
    }
}
