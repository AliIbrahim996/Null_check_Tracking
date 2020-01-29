/*
A code to track null-checks in java systems
1)Run this code  and choose any code  you want for testing // we used the class Customer in our package as a testing class .
2)Our code will implement some method and do some opeartions that  help the coder to avoid  null values .
3)Save your test results on the code you have chosen to any director with extension '.pdf'
 */
package tracking.pkgnull.check;

import static com.itextpdf.text.PageSize.A4;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;

import javafx.scene.text.Font;
import sun.font.FontFamily;

public class TrackingNullCheck {

    private ArrayList<String> codeLine = new ArrayList<>();
    private ArrayList<String> ifStatement = new ArrayList<>();
    private ArrayList<String> Null_comparand = new ArrayList<>();
    private ArrayList<String> Class_name = new ArrayList<>();
    private ArrayList<String> Null_Com_type = new ArrayList<>();
    private ArrayList<String> NC_Def = new ArrayList<>();
    int numofCondition;
    double numofMembers;
    double numofParameters;
    double numoflocals;
    private double numofMethodcall;
//Input  your chosen code  and store it in an array list named as codeLine

    public void readCodeLine(File file) throws FileNotFoundException, IOException {
        FileReader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);
        String s = "";
        while ((s = br.readLine()) != null) {
            codeLine.add(s);
        }
    }
//  A method to print the lines of the code on the output screen

    public void writeCodeLine() {
        for (String c : codeLine) {
            System.out.println(c);
        }
    }
// A method  to retrieve  all the if statements from the code .

    public void ExtractIfStatment() {
        for (String c : codeLine) {
            //  we consider  that the basic if statement  can determined as : 'if ( '  , in order to search whether the code lines contain the basic syntax of if.  
            if (c.toLowerCase().contains("if (")) {
                int index = c.indexOf("i");
                int index2 = c.indexOf("{");
                String ifst = c.substring(index, index2);
                if (!ifStatement.contains(ifst + " " + codeLine.indexOf(c))) {
                    ifStatement.add(ifst + " " + codeLine.indexOf(c));
                }
            }
        }
    }

    //  A method to print if statements of the code
    public String writeIfStatement() {
        String s = "";
        for (String c : ifStatement) {
            s += c.trim().substring(0, c.lastIndexOf(")") + 1) + "\n";
        }
        return s;
    }

    /*
    This is a method that extract all null comprands from if statments:
        there are 4 types of null checking:
        1) condition ==null;
        2) null== condition
        3) condition !=null
        4) null != condition
        As we notice the null-comprand may be in  left or right side .
        So all we have to do is to get the  index of two signs of these : '  = , ! , ( , ) ' ,
        substract the line from  the  first sign's idndex to the second sign's index
         and  store the returned string  in an arrayList named as Null_comparand .
     */
    public String Null_Check(String c) {

        if (c.contains("== null")) {
            int c1 = c.indexOf("(");
            int c2 = c.indexOf("=");
            Null_comparand.add(c.substring(c1 + 1, c2 - 1));
        } else if (c.contains(" != null")) {
            int c1 = c.indexOf("(");
            int c2 = c.indexOf("!");
            Null_comparand.add(c.substring(c1 + 1, c2 - 1));
        } else if (c.contains("null != ")) {
            int c1 = c.indexOf("=");
            Null_comparand.add(c.substring(c1 + 2, c.length()));
        } else if (c.contains("null == ")) {
            int c1 = c.indexOf("=");

            Null_comparand.add(c.substring(c1 + 3, c.length()));

        }
        return "";
    }

    /**/
    public String Write(ArrayList<String> a) {
        String msg = "";
        int i = 1;
        for (String s : a) {
            msg += "Null comparand[" + i + "] := " + s + "\n";
            i++;
        }
        return msg;
    }

    /**/
//A method to get the number of condition in a single if Statment.
    /* for each sign of these :  ' && , || '  , we increase the value of numofCondition by 1  */
    public boolean NumOfCondition(String If) {

        String replString = If;
        numofCondition = 0;
        String replString1 = "", replString2 = "";
        while (true) {
            if (replString.contains("&&")) {
                numofCondition++;
                int index1 = replString.indexOf("&");
                replString1 = replString.substring(0, index1 + 2).replace("&&", "$");
                replString2 = replString.substring(index1 + 2, replString.length());
                replString = replString1 + "" + replString2;

            } else if (replString.contains("||")) {
                numofCondition++;
                int index1 = replString.indexOf("|");
                replString1 = replString.substring(0, index1 + 2).replace("||", "$");
                replString2 = replString.substring(index1 + 2, replString.length());
                replString = replString1 + "" + replString2;

            } else {
                break;
            }
        }
        return numofCondition > 0;
    }
// This is a simple method to get the names of all classes in the code .

    public void getClassName() {
        for (String c_line : codeLine) {
            if (c_line.contains("class ")) {
                int i = c_line.indexOf("class ");
                int j = c_line.indexOf(" {");
                String cc = c_line.substring(i + 5, j);
                Class_name.add(cc);
            }
        }
    }

    /**/
 /*This method returns the Type of each null comparand */
    public void getNullComType() {
        for (String nc : Null_comparand) {
            //if the null comparand contains a '.' it maybe a method call or field access
            if (nc.contains(".")) {
                if (nc.contains("(")) {
                    Null_Com_type.add(nc + " ,Type := method call ");
                    numofMethodcall++;
                } else {
                    Null_Com_type.add(nc + " ,Type := field access");
                }
            } else {
                Null_Com_type.add(nc + " ,Type := Name");
            }
        }
    }

    /**/
 /*
    This method returns the type of each  null-comparan that determined as a name .
    - there are three types of names : ' member , local and parameter '
     */
    public String getNameExpType() {
        String msg = "";
        for (String nameExp : Null_Com_type) {
            boolean b = false;
            if (nameExp.contains("Name")) {
                int i = nameExp.indexOf(" ");
                String s = nameExp.substring(0, i);
                //search between class's name and constractor's declaration to find members
                for (String cName : Class_name) {
                    int ii = 0, jj = codeLine.size();
                    for (String cLine : codeLine) {
                        if (cLine.contains(cName + " {")) {
                            ii = codeLine.indexOf(cLine);

                        }
                        if (cLine.contains(cName + "(")) {
                            jj = codeLine.indexOf(cLine);
                            break;
                        }
                    }
                    for (i = ii + 1; i < jj + 1; i++) {
                        String c = codeLine.get(i);
                        String s1 = " " + s + ";";
                        if (c.contains(s1)) {
                            msg += "Null-c (" + s + ") Is a memeber variable" + "\n";
                            numofMembers++;
                            b = true;
                            break;
                        }
                    }
                    for (String cLine : codeLine) {
                        //if the null_c wasn't a member ,check whether it may be  a local or a parameter.
                        if (!b) {
                            //we know that the parameter will located between '(' and ')' of a method head .
                            if ((cLine.contains("(")) && cLine.contains(" " + s) && cLine.contains(" {")) {
                                String s1 = cLine.substring(cLine.indexOf("("), cLine.indexOf(")") + 1);
                                if (s1.contains(s + ")") || (s1.contains(", ") && s1.contains(s)) || s1.contains(s + ",")) {
                                    msg += "Null-c (" + s + ") Is a parameter variable" + "\n";
                                    numofParameters++;
                                    b = true;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (!b) {
                    //if it is not a parameter or a member it can be determined as a local clearly .
                    msg += "Null-c (" + s + ") Is a local variable" + "\n";
                    numoflocals++;
                }
            }

        }
        return msg;
    }

//Get  the defination of each null_c that is not a method .
    public void NC_exp() {
        for (String nc : Null_Com_type) {
            if (!nc.contains("method call")) {
                int i = nc.indexOf(" ");
                String s = nc.substring(0, i);
                int ii;
                String num = "";
                for (String ifSt : ifStatement) {
                    if (ifSt.contains(s + " ") || ifSt.contains(s + ")")) {
                        ii = ifSt.lastIndexOf(" ");
                        num = ifSt.substring(ii + 1, ifSt.length());
                        break;
                    }
                }
                ii = Integer.parseInt(num);
                for (i = 0; i < ii; i++) {
                    if (codeLine.get(i).contains(s + " =") && codeLine.get(i).contains(";")) {
                        NC_Def.add(codeLine.get(i).trim() + " " + i);

                    } else if (codeLine.get(i).contains(s + ";")) {
                        NC_Def.add(codeLine.get(i).trim() + " " + i);
                    }
                }
            }
        }

    }

    //for each defination we get the assigned value and give the coder a simple message to tell him the necessary of avoid nulls here.
    public String NC_value() {
        String msg = "";
        for (String ncDef : NC_Def) {
            if (ncDef.contains("=")) {
                int index1 = ncDef.indexOf("=");
                int index2 = ncDef.indexOf(";");
                String ncValue1 = ncDef.substring(0, index1);
                String ncValue2 = ncDef.substring(index1 + 2, index2);
                String lineNum = ncDef.substring(index2 + 2, ncDef.length());
                if (ncValue2.equals("null")) {
                    msg += "- " + ncValue2 + " assigned to " + ncValue1 + " in line" + lineNum + ", you should assign another value insteded of null" + "\n";
                } else if (ncValue2.charAt(0) != '(' && ncValue2.contains(")") && !ncValue2.contains("new")) {
                    msg += "- " + ncValue2 + " is a method call" + " assigned to " + ncValue1 + " in line " + lineNum + ", you should check your assigned value." + "\n\n";
                } else if (ncValue2.charAt(0) == '(') {
                    msg += "- " + ncValue2 + " is a casting " + " assigned to " + ncValue1 + " in line " + lineNum + ", you should check your assigned value." + "\n\n";
                } else if (ncValue2.contains("new")) {
                    msg += "- " + ncValue2 + " is a Object creation" + " assigned to " + ncValue1 + " in line " + lineNum + ", you should check your assigned value." + "\n\n";
                } else {
                    msg += "- " + ncValue2 + " is a reference value" + " assigned to " + ncValue1 + " in line " + lineNum + ", you should check your assigned value." + "\n\n";
                }
            } else {
                int index1 = ncDef.indexOf(";");
                int index2 = ncDef.indexOf(" ");
                String lineNum = ncDef.substring(index1 + 2, ncDef.length());
                msg += "- No assigned value to " + ncDef.substring(index2, index1) + " in line " + lineNum + ", you should assign any value " + "\n\n";
            }
        }
        return msg;
    }

    /*for each if statement we check whether it contains nulls then we calculate the number of condition in it.
    If   the number of condition in if statement > 0
    we call the methode RightValue on the statement and  decrease the number of condition by 1 .
    we repeat the above steps until the numofconditon is equal to zero  so we call the method Null_check  on last right condition
    else
    we just call method Null_check on if statment .
    
     */
    public void ifNullCheck() {

        for (String If : ifStatement) {
            if (If.contains("null")) {
                if (NumOfCondition(If)) {
                    String right = RightValue(If);
                    numofCondition--;
                    while (true) {
                        if (numofCondition == 0) {
                            Null_Check(right);
                            break;
                        }
                        right = RightValue(right);
                        numofCondition--;
                    }
                } else {
                    Null_Check(If);
                }
            }

        }
    }

    /*This method call Null_check on the first condition in if statment
    and return the rest conditions stored in the variable right */
    private String RightValue(String If) {
        String statment = "", right = "";
        int index1, index2, index;
// get the index of the first '&&' sign in the statment
        if (If.contains("&&")) {
            index = If.indexOf("(");
            if (If.charAt(index + 1) == '(') {
                index++;
            }
            index1 = If.indexOf("&&");
            //we see if there is an '||' sign before or after the '&&' sign  
            if (If.contains("||")) {
                index2 = If.indexOf("||");
                if (index1 > index2) {

                    statment = If.substring(index, index2 - 2);
                    Null_Check(statment);
                    right = If.substring(index2 + 3, If.length());

                } else {

                    statment = If.substring(index, index1 - 2);
                    Null_Check(statment);
                    right = If.substring(index1 + 3, If.length());
                }
            } else {
                /*if there is no '||' signs then extract the condition before the sign
                then run null_check on it
                and extract the rest conditions after the sign
                 */
                statment = If.substring(index, index1 - 2);

                Null_Check(statment);
                right = If.substring(index1 + 3, If.length());
            }
        } /*As above we repeat the same steps and check if the '||' sign  before or after '&&' sign */ else if (If.contains("||")) {
            index = If.indexOf("(");
            if (If.charAt(index + 1) == '(') {
                index++;
            }
            index1 = If.indexOf("||");
            if (If.contains("&&")) {
                index2 = If.indexOf("&&");
                if (index1 > index2) {

                    statment = If.substring(index, index2 - 2);
                    Null_Check(statment);
                    right = If.substring(index2 + 3, If.length());
                } else {

                    statment = If.substring(index, index1 - 2);
                    Null_Check(statment);
                    right = If.substring(index2 + 3, If.length());
                }
            } else {
                /*if there is not '&&' signs then extract the condition before the sign
                then run null_check on it
                and extract the rest conditions after the sign
                 */
                statment = If.substring(index, index1 - 2);

                Null_Check(statment);
                right = If.substring(index1 + 3, If.length());
            }
        }
        return right;
    }

    /* Method to print a report in a pdf file */
    public void printReport() {
        //create a file chooser to save a file to any directory
        JFileChooser jf = new JFileChooser(System.getProperty("user.dir", "."));
        jf.setDialogTitle("Specify a file to save");
        jf.showSaveDialog(null);
        File f = jf.getSelectedFile();
        //define a font  
        Font redText = new Font(FontFamily.TIMES_ROMAN, 12, 0, BaseColor.RED);
        Font grayText = new Font(FontFamily.TIMES_ROMAN, 15, 2, BaseColor.DARK_GRAY);
        //define Chunck what is this??!!
        /* simple information
        A chunck is a class in IText library
        A chunk is a string with font information
        A chunk dosen't add line breaks or a paragraph spacing
         */
        Chunk redTxt, grayTxt;
        //define a documnet
        Document doc = new Document(A4, 50, 50, 50, 50);
        Paragraph p;
        String newLine = System.getProperty("line.separator");
        try {
            PdfWriter.getInstance(doc, new FileOutputStream(f));
            String s = "";
            doc.open();
            redTxt = new Chunk("***** Tracking NULL Checks in Open-Source Java Systems *****", redText);
            //add chunk to paragraph
            p = new Paragraph(redTxt);
            doc.add(p);
            doc.add(new Paragraph(newLine + newLine + "- If statments in code: { " + newLine + writeIfStatement() + "}" + newLine));
            doc.add(new Paragraph("======================================================================" + newLine));
            doc.add(new Paragraph("- Null comprands : {" + newLine + Write(Null_comparand) + " }" + newLine));
            doc.add(new Paragraph("======================================================================" + newLine));
            doc.add(new Paragraph("-Null comprands type :{" + newLine));
            for (String s1 : Null_Com_type) {
                s += s1 + "\t" + newLine;
            }
            doc.add(new Paragraph(s));
            s = "";
            doc.add(new Paragraph("======================================================================" + newLine));
            doc.add(new Paragraph(getNameExpType() + newLine));
            doc.add(new Paragraph("======================================================================" + newLine));
            doc.add(new Paragraph("- Null comparands definition :" + newLine));
            for (String s1 : NC_Def) {
                s += "NC_Def := " + " ' " + s1.substring(0, s1.indexOf(";") + 1) + " ' " + newLine;
            }
            doc.add(new Paragraph(s));
            doc.add(new Paragraph("======================================================================" + newLine));
            redTxt = new Chunk(NC_value(), redText);
            p = new Paragraph(redTxt);
            doc.add(p);
            doc.add(new Paragraph("======================================================================" + newLine));
            double numofIf = ifStatement.size();
            double numOfNull_in_if = 0;
            for (String If : ifStatement) {
                if (If.contains("null")) {
                    numOfNull_in_if++;
                }

            }

            //calculate the average of all of the following in the code :
            /*
            1) if statments
            2)  members 
            3) parameters
            4) locals 
            5) method calls 
             */
            double avg1 = Math.round(numOfNull_in_if / numofIf * 100);
            double numofNull_c = Null_comparand.size();
            double avg2 = Math.round(numofMembers / numofNull_c * 100);
            double avg3 = Math.round(numofParameters / numofNull_c * 100);
            double avg4 = Math.round(numoflocals / numofNull_c * 100);
            double avg5 = Math.round(numofMethodcall / numofNull_c * 100);
            String msg = "Average of If statments that contains null :=\t" + avg1 + "%" + newLine
                    + "Average of Members from all Null_comparand :=\t" + avg2 + "%" + newLine
                    + "Average of Parameters from all Null_comparand :=\t" + avg3 + "%" + newLine
                    + "Average of Locals from all Null_comparand :=\t" + avg4 + "%" + newLine
                    + "Average of Methode calls from all Null_comparand :=\t" + avg5 + "%" + newLine;
            grayTxt = new Chunk(msg, grayText);
            p = new Paragraph(grayTxt);
            doc.add(p);
            doc.add(new Paragraph("======================================================================" + newLine));

            doc.close();
            JOptionPane.showMessageDialog(null, "File " + f + " have been saved successful");
        } catch (DocumentException | FileNotFoundException ex) {
            System.out.println(ex.getMessage());
        }
    }

    /**
     * @param args the command line arguments
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {

        TrackingNullCheck tnc = new TrackingNullCheck();
        String p = System.getProperty("user.dir");
        JFileChooser jFileChooser1 = new JFileChooser(p);
        if (jFileChooser1.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            File file = jFileChooser1.getSelectedFile();
            //read  the code lines
            tnc.readCodeLine(file);
            //extract If statements from the code lines
            tnc.ExtractIfStatment();
            // write the code
            tnc.writeCodeLine();
            //run Null_check method
            tnc.ifNullCheck();
            tnc.getClassName();
            //get the type of nullComparand 
            tnc.getNullComType();
            //get the defination of null_c
            tnc.NC_exp();
            //fianl step is to print report
            tnc.printReport();
        }

    }

}
