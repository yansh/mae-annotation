/*
 * MAE - Multi-purpose Annotation Environment
 *
 * Copyright Keigh Rim (krim@brandeis.edu)
 * Department of Computer Science, Brandeis University
 * Original program by Amber Stubbs (astubbs@cs.brandeis.edu)
 *
 * MAE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, @see <a href="http://www.gnu.org/licenses">http://www.gnu.org/licenses</a>.
 *
 * For feedback, reporting bugs, use the project on Github
 * @see <a href="https://github.com/keighrim/mae-annotation">https://github.com/keighrim/mae-annotation</a>.
 */

package edu.brandeis.cs.nlp.mae.agreement;

import edu.brandeis.cs.nlp.mae.agreement.calculator.GlobalAlphaUCalc;
import edu.brandeis.cs.nlp.mae.agreement.calculator.LocalAlphaUCalc;
import edu.brandeis.cs.nlp.mae.agreement.io.AbstractAnnotationIndexer;
import edu.brandeis.cs.nlp.mae.agreement.io.AnnotationFilesIndexer;
import edu.brandeis.cs.nlp.mae.agreement.io.XMLParseCache;
import edu.brandeis.cs.nlp.mae.database.MaeDBException;
import edu.brandeis.cs.nlp.mae.database.MaeDriverI;
import edu.brandeis.cs.nlp.mae.io.MaeIOException;
import edu.brandeis.cs.nlp.mae.io.MaeXMLParser;
import edu.brandeis.cs.nlp.mae.io.ParsedAtt;
import edu.brandeis.cs.nlp.mae.io.ParsedTag;
import edu.brandeis.cs.nlp.mae.util.FileHandler;
import edu.brandeis.cs.nlp.mae.util.MappedSet;
import edu.brandeis.cs.nlp.mae.util.SpanHandler;
import org.dkpro.statistics.agreement.unitizing.KrippendorffAlphaUnitizingAgreement;
import org.dkpro.statistics.agreement.unitizing.UnitizingAnnotationStudy;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import static edu.brandeis.cs.nlp.mae.agreement.MaeAgreementStrings.*;

/**
 * Created by krim on 4/14/2016.
 */
public class MaeAgreementMain {

    private int KAPPA_COEFFICIENT = 0;
    private int ALPHA_COEFFICIENT = 1;
    private int ALPHAU_COEFFICIENT = 2;

    private AbstractAnnotationIndexer fileIdx;
    private MaeDriverI driver;
    private XMLParseCache parseCache;
    private int[] documentLength;
    private int totalDocumentsLength;
    private int numAnnotators;

    public MaeAgreementMain(MaeDriverI driver) {
        this.driver = driver;
    }

    public void loadAnnotationFiles(File singleDir) throws MaeIOException, IOException, SAXException, MaeDBException {

        if (!FileHandler.containsDirsOnly(singleDir)) {
            fileIdx = new AnnotationFilesIndexer();
//        } else {
            // TODO: 2016-04-23 17:03:30EDT  implement indexer from dirs
//            fileIdx = new AnnotationDirsIndexer();
        }
        fileIdx.indexAnnotations(singleDir);
        validateTaskNames(driver.getTaskName());
        validateTextSharing();
        parseCache = new XMLParseCache(driver, fileIdx);

        numAnnotators = fileIdx.getAnnotators().size();
        totalDocumentsLength = IntStream.of(documentLength).reduce( 0,(a, b) -> a + b);
    }

    boolean validateTaskNames(String taskName) throws IOException, SAXException {
        MaeXMLParser parser = new MaeXMLParser();
        for (String docName : fileIdx.getDocumentNames()) {
            for (String fileName : fileIdx.getAnnotationsOfDocument(docName)) {
                if (fileName != null && !parser.isTaskNameMatching(new File(fileName), taskName)) {
                    return false;
                }
            }
        }
        return true;
    }

    boolean validateTextSharing() throws IOException, SAXException {
        MaeXMLParser parser = new MaeXMLParser();
        documentLength = new int[fileIdx.getDocumentNames().size()];
        int curDoc = 0;
        for (String docName : fileIdx.getDocumentNames()) {
            String[] fileNames = fileIdx.getAnnotationsOfDocument(docName);
            int seen = getFirstNonNullIndex(fileNames);
            parser.readAnnotationPreamble(new File(fileNames[seen++]));
            String primaryText = parser.getParsedPrimaryText();
            documentLength[curDoc++] = primaryText.length();
            for (int i = seen; i < fileNames.length; i++) {
                if (fileNames[i] != null && !parser.isPrimaryTextMatching(new File(fileNames[i]), primaryText)) {
                    return false;
                }
            }
        }
        return true;
    }


    private static int countNonNull(Object[] array) {
        int countNonNull = 0;
        for (Object obj : array) {
            if (obj != null) {
                countNonNull++;
            }
        }
        return countNonNull;
    }

    private static int getFirstNonNullIndex(Object[] array) {
        int seen = 0;
        while (seen < array.length && array[seen] == null) {
            seen++;
        }
        return seen;
    }

    public String agreementsToString(String agreementType, Map<String, Double> agreements) {
        String results = agreementType + "\n\n";
        for (String agreementKey : agreements.keySet()) {
            results += agreementToString(agreementType, agreementKey, agreements.get(agreementKey));
        }
        results += "\n";
        return results;
    }

    public String agreementToString(String agrType, String agrKey, Double agr) {
        return String.format("% .4f (%s) %s\n", agr, agrType, agrKey );
    }

    public Map<String, Double> calculateLocalAlphaU(MappedSet<String, String> targetTagsAndAtts) throws IOException, SAXException, MaeDBException {
        LocalAlphaUCalc calc = new LocalAlphaUCalc(fileIdx, parseCache, documentLength);
        return calc.calculateAgreement(targetTagsAndAtts);
    }

    public Map<String, Double> calculateGlobalAlphaU(MappedSet<String, String> targetTagsAndAtts) throws IOException, SAXException, MaeDBException {
        GlobalAlphaUCalc calc = new GlobalAlphaUCalc(fileIdx, parseCache, documentLength);
        return calc.calculateAgreement(targetTagsAndAtts);
    }

   public boolean isIgnoreMissing() {
        // TODO: 2016-04-20 00:06:05EDT implement this as an option, also amend code searchable by 'annotator++'
        return false;
    }
}
