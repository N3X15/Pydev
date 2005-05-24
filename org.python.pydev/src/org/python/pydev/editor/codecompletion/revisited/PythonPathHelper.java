/*
 * Created on Nov 12, 2004
 *
 * @author Fabio Zadrozny
 */
package org.python.pydev.editor.codecompletion.revisited;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.python.pydev.plugin.PydevPlugin;

/**
 * This is not a singleton because we may have a different pythonpath for each project (even though
 * we have a default one as the original pythonpath).
 * 
 * @author Fabio Zadrozny
 */
public class PythonPathHelper implements Serializable{
    
    /**
     * This is a list of Files containg the pythonpath.
     */
    public List pythonpath = new ArrayList();

    /**
     * Modules that we have in memory. This is persisted when saved.
     * 
     * Keys are strings with the name of the module. Values are AbstractModule objects.
     */
    Map modules = new HashMap();    
    
    /**
     * Returns the default path given from the string.
     * @param str
     * @param acceptPoint
     * @return
     */
    public String getDefaultPathStr(String str, boolean acceptPoint){
        if((str.indexOf("\\.") != -1 || str.indexOf(".") == 1) && acceptPoint == false){
            throw new RuntimeException("The pythonpath can only have full paths without . or .. ("+str+") is not valid.");
        }
        if(str.indexOf(':') == 1){
            String drive = str.substring(0,1).toLowerCase();
            str = drive + str.substring(1);
        }
        return str.trim().replaceAll("\\\\","/");//.toLowerCase();
    }
    
    public String getDefaultPathStr(String str){
        return getDefaultPathStr(str, false);
    }
    
    /**
     * This method returns all modules that can be obtained from a root File.
     * @param monitor
     * @return the files in position 0 and folders in position 1.
     */
    public List[] getModulesBelow(File root, IProgressMonitor monitor){
        FileFilter filter = new FileFilter() {
            
	        public boolean accept(File pathname) {
	            if(pathname.isFile()){
	                return isValidFileMod(pathname.getAbsolutePath());
	            }else if(pathname.isDirectory()){
	                return isFileOrFolderWithInit(pathname);
	            }else{
	                return false;
	            }
	        }
	
	    };
	    return PydevPlugin.getPyFilesBelow(root, filter, monitor);
    }
    

    public static boolean isValidDll(String path){
        if( path.endsWith(".pyd")  ||
	        path.endsWith(".so")  ||
	        path.endsWith(".dll")){
            return true;
        }
        return false;
    }
    
    /**
     * 
     * @param path
     * @return if the paths maps to a valid python module (depending on its extension).
     */
    public static boolean isValidFileMod(String path){
//           path.endsWith(".pyc")  || - we don't want pyc files, only source files and compiled extensions
//           path.endsWith(".pyo") - we don't want pyo files, only source files and compiled extensions

        if( path.endsWith(".py") || 
            path.endsWith(".pyw")){
             return true;
        }
        
        else if(isValidDll(path)){
            return true;
        }
        
        return false;
    }
    
    
    public String resolveModule(String fullPath){
        return resolveModule(fullPath, true);
    }
    
    /**
     * DAMN... when I started thinking this up, it seemed much better... (and easier)
     * 
     * @param module - this is the full path of the module. Only for directories or py,pyd,dll,pyo files.
     * @return a String with the module that the file or folder should represent. E.g.: compiler.ast
     */
    public String resolveModule(String fullPath, boolean requireFileToExist){
        fullPath = getDefaultPathStr(fullPath, true);
        File moduleFile = new File(fullPath);
        
        if(requireFileToExist){
	        if(moduleFile.exists() == false){
	            return null;
	        }
        }
        if(moduleFile.isFile()){
            
            if(isValidFileMod(moduleFile.getAbsolutePath()) == false){
                return null;
            }
        }
        
        //go through our pythonpath and check the beggining
        for (Iterator iter = pythonpath.iterator(); iter.hasNext();) {
            
            String element = getDefaultPathStr((String) iter.next());
            if(fullPath.startsWith(element)){
                String s = fullPath.substring(element.length());
                if(s.startsWith("/")){
                    s = s.substring(1);
                }
                s = s.replaceAll("/",".");
                
            
                //if it is a valid module, let's find out if it exists...
                if(isValidModule(s)){
                    if(s.indexOf(".") != -1){
                        File root = new File(element);
                        if(root.exists() == false){
                            continue;
                        }
                        
                        //this means that more than 1 module is specified, so, in order to get it,
                        //we have to go and see if all the folders to that module have __init__.py in it...
                        String[] modulesParts = s.split("\\.");
                        
                        if(modulesParts.length > 1 && moduleFile.isFile()){
                            String[] t = new String[modulesParts.length -1];
                            
                            for (int i = 0; i < modulesParts.length-1; i++) {
                                t[i] = modulesParts[i];
                            }
                            t[t.length -1] = t[t.length -1]+"."+modulesParts[modulesParts.length-1];
                            modulesParts = t;
                        }
                        
                        //here, in modulesParts, we have something like 
                        //["compiler", "ast.py"] - if file
                        //["pywin","debugger"] - if folder
                        //
                        //root starts with the pythonpath folder that starts with the same
                        //chars as the full path passed in.
                        boolean isValid = true;
                        for (int i = 0; i < modulesParts.length && root != null; i++) {
                            
                            //check if file is in root...
                            if(isValidFileMod(modulesParts[i])){
                                root = new File(root.getAbsolutePath() + "/" + modulesParts[i]);
                                if(root.exists() && root.isFile()){
                                    break;
                                }
                                
                            }else{
                                //this part is a folder part... check if it is a valid module (has init).
	                            root = new File(root.getAbsolutePath() + "/" + modulesParts[i]);
	                            if(isFileOrFolderWithInit(root) == false){
	                                isValid = false;
	                                break;
	                            }
	                            //go on and check the next part.
                            }                            
                        }
                        if(isValid){
                            if(moduleFile.isFile()){
                                s = stripExtension(s);
                            }
	                        return s;
                        }
                    }else{
                        //simple part, we don't have to go into subfolders to check validity...
                        if(moduleFile.isFile()){
                            throw new RuntimeException("This should never happen... if it is a file, it always has a dot, so, this should not happen...");
                        }else if (moduleFile.isDirectory() && isFileOrFolderWithInit(moduleFile) == false){
                            return null;
                        }
                        return s;
                    }
                }
            }
            
        }
        return null;
    }

    /**
     * Note that this function is not completely safe...beware when using it.
     * @param s
     * @return
     */
    public static String stripExtension(String s) {
        if(s != null){
	        String[] strings = s.split("\\.");
	        return s.substring(0, s.length() - strings[strings.length -1].length() -1);
        }
        return null;
    }

    /**
     * @param root
     * @param string
     * @return
     */
    private boolean isFileOrFolderWithInit(File root) {
        if(root.isDirectory() == false){
            return true;
        }
        
        File[] files = root.listFiles();
        
        if(files == null){
            return false;
        }
        
        //check for an __init__.py
        String[] items = root.list();
        for (int j = 0; j < items.length; j++) {
            if(items[j].toLowerCase().equals("__init__.py")){
                return true;
            }
        }
        
        return false;
    }

    /**
     * @param s
     * @return
     */
    private boolean isValidModule(String s) {
        return s.indexOf("-") == -1;
    }

    /**
     * @param string
     * @return
     */
    public List setPythonPath(String string) {
        String[] strings = string.split("\\|");
        for (int i = 0; i < strings.length; i++) {
            pythonpath.add(getDefaultPathStr(strings[i]));
        }
        return Arrays.asList(strings);
    }

    /**
     * @param f
     * @return
     */
    public static String getPythonFileEncoding(File f) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
            try{
                
                String l1 = reader.readLine();
                String l2 = reader.readLine();
                
                String lEnc = null;
                //encoding must be specified in first or second line...
                if (l1 != null && l1.toLowerCase().indexOf("coding") != -1){
                    lEnc = l1; 
                }
                else if (l2 != null && l2.toLowerCase().indexOf("coding") != -1){
                    lEnc = l2; 
                }
                else{
                    return null;
                }
                
                //ok, the encoding line is in lEnc
                lEnc = lEnc.substring(lEnc.indexOf("coding")+6);
                
                char c;
                while(lEnc.length() > 0 && ((c = lEnc.charAt(0)) == ' ' || c == ':' || c == '=')) {
                    lEnc = lEnc.substring(1);
                }

                StringBuffer buffer = new StringBuffer();
                while(lEnc.length() > 0 && ((c = lEnc.charAt(0)) != ' ' || c == '-' || c == '*')) {
                    
                    buffer.append(c);
                    lEnc = lEnc.substring(1);
                }

                return buffer.toString();
                
            } catch (IOException e) {
                e.printStackTrace();
            }finally{
                try {reader.close();} catch (IOException e1) {}
            }
        } catch (FileNotFoundException e) {
            return null;
        }
        return null;
    }
}
