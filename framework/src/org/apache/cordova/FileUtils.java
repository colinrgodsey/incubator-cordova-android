/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;

import org.apache.commons.codec.binary.Base64;
import org.apache.cordova.api.CordovaInterface;
import org.apache.cordova.api.Plugin;
import org.apache.cordova.api.PluginResult;
import org.apache.cordova.file.EncodingException;
import org.apache.cordova.file.FileExistsException;
import org.apache.cordova.file.InvalidModificationException;
import org.apache.cordova.file.NoModificationAllowedException;
import org.apache.cordova.file.TypeMismatchException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;


/**
 * This class provides SD card file and directory services to JavaScript.
 * Only files on the SD card can be accessed.
 */
public class FileUtils extends Plugin {
    private static final String LOG_TAG = "FileUtils";
    private static final String _DATA = "_data";    // The column name where the file path is stored

    public static int NOT_FOUND_ERR = 1;
    public static int SECURITY_ERR = 2;
    public static int ABORT_ERR = 3;

    public static int NOT_READABLE_ERR = 4;
    public static int ENCODING_ERR = 5;
    public static int NO_MODIFICATION_ALLOWED_ERR = 6;
    public static int INVALID_STATE_ERR = 7;
    public static int SYNTAX_ERR = 8;
    public static int INVALID_MODIFICATION_ERR = 9;
    public static int QUOTA_EXCEEDED_ERR = 10;
    public static int TYPE_MISMATCH_ERR = 11;
    public static int PATH_EXISTS_ERR = 12;

    public static int TEMPORARY = 0;
    public static int PERSISTENT = 1;
    public static int RESOURCE = 2;
    public static int APPLICATION = 3;

    FileReader f_in;
    FileWriter f_out;

    /**
     * Constructor.
     */
    public FileUtils() {
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action 		The action to execute.
     * @param args 			JSONArry of arguments for the plugin.
     * @param callbackId	The callback id used when calling back into JavaScript.
     * @return 				A PluginResult object with a status and message.
     */
    public PluginResult execute(String action, JSONArray args, String callbackId) {
        PluginResult.Status status = PluginResult.Status.OK;
        String result = "";
        //System.out.println("FileUtils.execute("+action+")");

        try {
            if (action.equals("testSaveLocationExists")) {
                boolean b = DirectoryManager.testSaveLocationExists();
                return new PluginResult(status, b);
            }
            else if (action.equals("getFreeDiskSpace")) {
                long l = DirectoryManager.getFreeDiskSpace(false);
                return new PluginResult(status, l);
            }
            else if (action.equals("testFileExists")) {
                boolean b = DirectoryManager.testFileExists(args.getString(0));
                return new PluginResult(status, b);
            }
            else if (action.equals("testDirectoryExists")) {
                boolean b = DirectoryManager.testFileExists(args.getString(0));
                return new PluginResult(status, b);
            }
            else if (action.equals("readAsText")) {
                String s = this.readAsText(args.getString(0), args.getString(1));
                return new PluginResult(status, s);
            }
            else if (action.equals("readAsDataURL")) {
                String s = this.readAsDataURL(args.getString(0));
                return new PluginResult(status, s);
            }
            else if (action.equals("write")) {
                long fileSize = this.write(args.getString(0), args.getString(1), args.getInt(2));
                return new PluginResult(status, fileSize);
            }
            else if (action.equals("truncate")) {
                long fileSize = this.truncateFile(args.getString(0), args.getLong(1));
                return new PluginResult(status, fileSize);
            }
            else if (action.equals("requestFileSystem")) {
                long size = args.optLong(1);
                if (size != 0) {
                    if (size > (DirectoryManager.getFreeDiskSpace(true)*1024)) {
                        return new PluginResult(PluginResult.Status.ERROR, FileUtils.QUOTA_EXCEEDED_ERR);
                    }
                }
                JSONObject obj = requestFileSystem(args.getInt(0));
                return new PluginResult(status, obj);
            }
            else if (action.equals("resolveLocalFileSystemURI")) {
                JSONObject obj = resolveLocalFileSystemURI(args.getString(0));
                return new PluginResult(status, obj);
            }
            else if (action.equals("getMetadata")) {
                JSONObject obj = getMetadata(args.getString(0));
                return new PluginResult(status, obj);
            }
            else if (action.equals("getFileMetadata")) {
                JSONObject obj = getFileMetadata(args.getString(0));
                return new PluginResult(status, obj);
            }
            else if (action.equals("getParent")) {
                JSONObject obj = getParent(args.getString(0));
                return new PluginResult(status, obj);
            }
            else if (action.equals("getDirectory")) {
                JSONObject obj = getFile(args.getString(0), args.getString(1), args.optJSONObject(2), true);
                return new PluginResult(status, obj);
            }
            else if (action.equals("getFile")) {
                JSONObject obj = getFile(args.getString(0), args.getString(1), args.optJSONObject(2), false);
                return new PluginResult(status, obj);
            }
            else if (action.equals("remove")) {
                boolean success;

                success = remove(args.getString(0));

                if (success) {
                    notifyDelete(args.getString(0));
                    return new PluginResult(status);
                } else {
                    return new PluginResult(PluginResult.Status.ERROR, FileUtils.NO_MODIFICATION_ALLOWED_ERR);
                }
            }
            else if (action.equals("removeRecursively")) {
                boolean success = removeRecursively(args.getString(0));
                if (success) {
                    return new PluginResult(status);
                } else {
                    return new PluginResult(PluginResult.Status.ERROR, FileUtils.NO_MODIFICATION_ALLOWED_ERR);
                }
            }
            else if (action.equals("moveTo")) {
                JSONObject entry = transferTo(args.getString(0), args.getString(1), args.getString(2), true);
                return new PluginResult(status, entry);
            }
            else if (action.equals("copyTo")) {
                JSONObject entry = transferTo(args.getString(0), args.getString(1), args.getString(2), false);
                return new PluginResult(status, entry);
            }
            else if (action.equals("readEntries")) {
                JSONArray entries = readEntries(args.getString(0));
                return new PluginResult(status, entries);
            }
            return new PluginResult(status, result);
        } catch (FileNotFoundException e) {
            return new PluginResult(PluginResult.Status.ERROR, FileUtils.NOT_FOUND_ERR);
        } catch (FileExistsException e) {
            return new PluginResult(PluginResult.Status.ERROR, FileUtils.PATH_EXISTS_ERR);
        } catch (NoModificationAllowedException e) {
            return new PluginResult(PluginResult.Status.ERROR, FileUtils.NO_MODIFICATION_ALLOWED_ERR);
        } catch (JSONException e) {
            return new PluginResult(PluginResult.Status.ERROR, FileUtils.NO_MODIFICATION_ALLOWED_ERR);
        } catch (InvalidModificationException e) {
            return new PluginResult(PluginResult.Status.ERROR, FileUtils.INVALID_MODIFICATION_ERR);
        } catch (MalformedURLException e) {
            return new PluginResult(PluginResult.Status.ERROR, FileUtils.ENCODING_ERR);
        } catch (IOException e) {
            return new PluginResult(PluginResult.Status.ERROR, FileUtils.INVALID_MODIFICATION_ERR);
        } catch (EncodingException e) {
            return new PluginResult(PluginResult.Status.ERROR, FileUtils.ENCODING_ERR);
        } catch (TypeMismatchException e) {
            return new PluginResult(PluginResult.Status.ERROR, FileUtils.TYPE_MISMATCH_ERR);
        }
    }

    /**
     * Need to check to see if we need to clean up the content store
     *
     * @param filePath the path to check
     */
    private void notifyDelete(String filePath) {
        int result = this.ctx.getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media.DATA + " = ?",
                new String[] {filePath});
    }

    /**
     * Allows the user to look up the Entry for a file or directory referred to by a local URI.
     *
     * @param url of the file/directory to look up
     * @return a JSONObject representing a Entry from the filesystem
     * @throws MalformedURLException if the url is not valid
     * @throws FileNotFoundException if the file does not exist
     * @throws IOException if the user can't read the file
     * @throws JSONException
     */
    private JSONObject resolveLocalFileSystemURI(String url) throws IOException, JSONException {
        String decoded = URLDecoder.decode(url, "UTF-8");

        File fp = null;

        // Handle the special case where you get an Android content:// uri.
        if (decoded.startsWith("content:")) {
            Cursor cursor = this.ctx.managedQuery(Uri.parse(decoded), new String[] { MediaStore.Images.Media.DATA }, null, null, null);
            // Note: MediaStore.Images/Audio/Video.Media.DATA is always "_data"
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            fp = new File(cursor.getString(column_index));
        } else {
            // Test to see if this is a valid URL first
            @SuppressWarnings("unused")
            URL testUrl = new URL(decoded);

            if (decoded.startsWith("file://")) {
                fp = new File(decoded.substring(7, decoded.length()));
            } else {
                fp = new File(decoded);
            }
        }

        if (!fp.exists()) {
            throw new FileNotFoundException();
        }
        if (!fp.canRead()) {
            throw new IOException();
        }
        return getEntry(fp);
    }

    /**
     * Read the list of files from this directory.
     *
     * @param fileName the directory to read from
     * @return a JSONArray containing JSONObjects that represent Entry objects.
     * @throws FileNotFoundException if the directory is not found.
     * @throws JSONException
     */
    private JSONArray readEntries(String fileName) throws FileNotFoundException, JSONException {
        File fp = createFileObject(fileName);

        if (!fp.exists()) {
            // The directory we are listing doesn't exist so we should fail.
            throw new FileNotFoundException();
        }

        JSONArray entries = new JSONArray();

        if (fp.isDirectory()) {
            File[] files = fp.listFiles();
            for (int i=0; i<files.length; i++) {
                entries.put(getEntry(files[i]));
            }
        }

        return entries;
    }

    /**
     * A setup method that handles the move/copy of files/directories
     *
     * @param fileName to be copied/moved
     * @param newParent is the location where the file will be copied/moved to
     * @param newName for the file directory to be called, if null use existing file name
     * @param move if false do a copy, if true do a move
     * @return a Entry object
     * @throws NoModificationAllowedException
     * @throws IOException
     * @throws InvalidModificationException
     * @throws EncodingException
     * @throws JSONException
     */
    private JSONObject transferTo(String fileName, String newParent, String newName, boolean move) throws JSONException, NoModificationAllowedException, IOException, InvalidModificationException, EncodingException {
        fileName = stripFileProtocol(fileName);
        newParent = stripFileProtocol(newParent);


        // Check for invalid file name
        if (newName != null && newName.contains(":")) {
            throw new EncodingException("Bad file name");
        }

        File source = new File(fileName);

        if (!source.exists()) {
            // The file/directory we are copying doesn't exist so we should fail.
            throw new FileNotFoundException("The source does not exist");
        }

        File destinationDir = new File(newParent);
        if (!destinationDir.exists()) {
            // The destination does not exist so we should fail.
            throw new FileNotFoundException("The source does not exist");
        }

        // Figure out where we should be copying to
        File destination = createDestination(newName, source, destinationDir);

        //Log.d(LOG_TAG, "Source: " + source.getAbsolutePath());
        //Log.d(LOG_TAG, "Destin: " + destination.getAbsolutePath());

        // Check to see if source and destination are the same file
        if (source.getAbsolutePath().equals(destination.getAbsolutePath())) {
            throw new InvalidModificationException("Can't copy a file onto itself");
        }

        if (source.isDirectory()) {
            if (move) {
                return moveDirectory(source, destination);
            } else {
                return copyDirectory(source, destination);
            }
        } else {
            if (move) {
                return moveFile(source, destination);
            } else {
                return copyFile(source, destination);
            }
        }
    }

    /**
     * Creates the destination File object based on name passed in
     *
     * @param newName for the file directory to be called, if null use existing file name
     * @param fp represents the source file
     * @param destination represents the destination file
     * @return a File object that represents the destination
     */
    private File createDestination(String newName, File fp, File destination) {
        File destFile = null;

        // I know this looks weird but it is to work around a JSON bug.
        if ("null".equals(newName) || "".equals(newName) ) {
            newName = null;
        }

        if (newName != null) {
            destFile = new File(destination.getAbsolutePath() + File.separator + newName);
        } else {
            destFile = new File(destination.getAbsolutePath() + File.separator + fp.getName());
        }
        return destFile;
    }

    /**
     * Copy a file
     *
     * @param srcFile file to be copied
     * @param destFile destination to be copied to
     * @return a FileEntry object
     * @throws IOException
     * @throws InvalidModificationException
     * @throws JSONException
     */
    private JSONObject copyFile(File srcFile, File destFile) throws IOException, InvalidModificationException, JSONException  {
        // Renaming a file to an existing directory should fail
        if (destFile.exists() && destFile.isDirectory()) {
            throw new InvalidModificationException("Can't rename a file to a directory");
        }

        FileChannel input = new FileInputStream(srcFile).getChannel();
        FileChannel output = new FileOutputStream(destFile).getChannel();

        input.transferTo(0, input.size(), output);

        input.close();
        output.close();

        /*
        if (srcFile.length() != destFile.length()) {
            return false;
        }
        */

        return getEntry(destFile);
    }

    /**
     * Copy a directory
     *
     * @param srcDir directory to be copied
     * @param destinationDir destination to be copied to
     * @return a DirectoryEntry object
     * @throws JSONException
     * @throws IOException
     * @throws NoModificationAllowedException
     * @throws InvalidModificationException
     */
    private JSONObject copyDirectory(File srcDir, File destinationDir) throws JSONException, IOException, NoModificationAllowedException, InvalidModificationException {
        // Renaming a file to an existing directory should fail
        if (destinationDir.exists() && destinationDir.isFile()) {
            throw new InvalidModificationException("Can't rename a file to a directory");
        }

        // Check to make sure we are not copying the directory into itself
        if (isCopyOnItself(srcDir.getAbsolutePath(), destinationDir.getAbsolutePath())) {
            throw new InvalidModificationException("Can't copy itself into itself");
        }

        // See if the destination directory exists. If not create it.
        if (!destinationDir.exists()) {
            if (!destinationDir.mkdir()) {
                // If we can't create the directory then fail
                throw new NoModificationAllowedException("Couldn't create the destination direcotry");
            }
        }

        for (File file : srcDir.listFiles()) {
            if (file.isDirectory()) {
                copyDirectory(file, destinationDir);
            } else {
                File destination = new File(destinationDir.getAbsoluteFile() + File.separator + file.getName());
                copyFile(file, destination);
            }
        }

        return getEntry(destinationDir);
    }

    /**
     * Check to see if the user attempted to copy an entry into its parent without changing its name,
     * or attempted to copy a directory into a directory that it contains directly or indirectly.
     *
     * @param srcDir
     * @param destinationDir
     * @return
     */
    private boolean isCopyOnItself(String src, String dest) {

        // This weird test is to determine if we are copying or moving a directory into itself.
        // Copy /sdcard/myDir to /sdcard/myDir-backup is okay but
        // Copy /sdcard/myDir to /sdcard/myDir/backup should thow an INVALID_MODIFICATION_ERR
        if (dest.startsWith(src) && dest.indexOf(File.separator, src.length()-1) != -1) {
            return true;
        }

        return false;
    }

    /**
     * Move a file
     *
     * @param srcFile file to be copied
     * @param destFile destination to be copied to
     * @return a FileEntry object
     * @throws IOException
     * @throws InvalidModificationException
     * @throws JSONException
     */
    private JSONObject moveFile(File srcFile, File destFile) throws JSONException, InvalidModificationException {
        // Renaming a file to an existing directory should fail
        if (destFile.exists() && destFile.isDirectory()) {
            throw new InvalidModificationException("Can't rename a file to a directory");
        }

        // Try to rename the file
        if (!srcFile.renameTo(destFile)) {
            // Trying to rename the file failed.  Possibly because we moved across file system on the device.
            // Now we have to do things the hard way
            // 1) Copy all the old file
            // 2) delete the src file
        }

        return getEntry(destFile);
    }

    /**
     * Move a directory
     *
     * @param srcDir directory to be copied
     * @param destinationDir destination to be copied to
     * @return a DirectoryEntry object
     * @throws JSONException
     * @throws IOException
     * @throws InvalidModificationException
     */
    private JSONObject moveDirectory(File srcDir, File destinationDir) throws JSONException, InvalidModificationException {
        // Renaming a file to an existing directory should fail
        if (destinationDir.exists() && destinationDir.isFile()) {
            throw new InvalidModificationException("Can't rename a file to a directory");
        }

        // Check to make sure we are not copying the directory into itself
        if (isCopyOnItself(srcDir.getAbsolutePath(), destinationDir.getAbsolutePath())) {
            throw new InvalidModificationException("Can't move itself into itself");
        }

        // If the destination directory already exists and is empty then delete it.  This is according to spec.
        if (destinationDir.exists()) {
            if (destinationDir.list().length > 0) {
                throw new InvalidModificationException("directory is not empty");
            }
        }

        // Try to rename the directory
        if (!srcDir.renameTo(destinationDir)) {
            // Trying to rename the directory failed.  Possibly because we moved across file system on the device.
            // Now we have to do things the hard way
            // 1) Copy all the old files
            // 2) delete the src directory
        }

        return getEntry(destinationDir);
    }

    /**
     * Deletes a directory and all of its contents, if any. In the event of an error
     * [e.g. trying to delete a directory that contains a file that cannot be removed],
     * some of the contents of the directory may be deleted.
     * It is an error to attempt to delete the root directory of a filesystem.
     *
     * @param filePath the directory to be removed
     * @return a boolean representing success of failure
     * @throws FileExistsException
     */
    private boolean removeRecursively(String filePath) throws FileExistsException {
        File fp = createFileObject(filePath);

        // You can't delete the root directory.
        if (atRootDirectory(filePath)) {
            return false;
        }

        return removeDirRecursively(fp);
    }

    /**
     * Loops through a directory deleting all the files.
     *
     * @param directory to be removed
     * @return a boolean representing success of failure
     * @throws FileExistsException
     */
    private boolean removeDirRecursively(File directory) throws FileExistsException {
        if (directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                removeDirRecursively(file);
            }
        }

        if (!directory.delete()) {
            throw new FileExistsException("could not delete: " + directory.getName());
        } else {
            return true;
        }
    }

    /**
     * Deletes a file or directory. It is an error to attempt to delete a directory that is not empty.
     * It is an error to attempt to delete the root directory of a filesystem.
     *
     * @param filePath file or directory to be removed
     * @return a boolean representing success of failure
     * @throws NoModificationAllowedException
     * @throws InvalidModificationException
     */
    private boolean remove(String filePath) throws NoModificationAllowedException, InvalidModificationException {
        File fp = createFileObject(filePath);

        // You can't delete the root directory.
        if (atRootDirectory(filePath)) {
            throw new NoModificationAllowedException("You can't delete the root directory");
        }

        // You can't delete a directory that is not empty
        if (fp.isDirectory() && fp.list().length > 0) {
            throw new InvalidModificationException("You can't delete a directory that is not empty.");
        }

        return fp.delete();
    }

    /**
     * Creates or looks up a file.
     *
     * @param dirPath base directory
     * @param fileName file/directory to lookup or create
     * @param options specify whether to create or not
     * @param directory if true look up directory, if false look up file
     * @return a Entry object
     * @throws FileExistsException
     * @throws IOException
     * @throws TypeMismatchException
     * @throws EncodingException
     * @throws JSONException
     */
    private JSONObject getFile(String dirPath, String fileName, JSONObject options, boolean directory) throws FileExistsException, IOException, TypeMismatchException, EncodingException, JSONException {
        boolean create = false;
        boolean exclusive = false;
        if (options != null) {
            create = options.optBoolean("create");
            if (create) {
                exclusive = options.optBoolean("exclusive");
            }
        }

        // Check for a ":" character in the file to line up with BB and iOS
        if (fileName.contains(":")) {
            throw new EncodingException("This file has a : in it's name");
        }

        File fp = createFileObject(dirPath, fileName);

        if (create) {
            if (exclusive && fp.exists()) {
                throw new FileExistsException("create/exclusive fails");
            }
            if (directory) {
                fp.mkdir();
            } else {
                fp.createNewFile();
            }
            if (!fp.exists()) {
                throw new FileExistsException("create fails");
            }
        }
        else {
            if (!fp.exists()) {
                throw new FileNotFoundException("path does not exist");
            }
            if (directory) {
                if (fp.isFile()) {
                    throw new TypeMismatchException("path doesn't exist or is file");
                }
            } else {
                if (fp.isDirectory()) {
                    throw new TypeMismatchException("path doesn't exist or is directory");
                }
            }
        }

        // Return the directory
        return getEntry(fp);
    }

    /**
     * If the path starts with a '/' just return that file object. If not construct the file
     * object from the path passed in and the file name.
     *
     * @param dirPath root directory
     * @param fileName new file name
     * @return
     */
    private File createFileObject(String dirPath, String fileName) {
        File fp = null;
        if (fileName.startsWith("/")) {
            fp = new File(fileName);
        } else {
            dirPath = stripFileProtocol(dirPath);
            fp = new File(dirPath + File.separator + fileName);
        }
        return fp;
    }

    /**
     * Look up the parent DirectoryEntry containing this Entry.
     * If this Entry is the root of its filesystem, its parent is itself.
     *
     * @param filePath
     * @return
     * @throws JSONException
     */
    private JSONObject getParent(String filePath) throws JSONException {
        filePath = stripFileProtocol(filePath);

        if (atRootDirectory(filePath)) {
            return getEntry(filePath);
        }
        return getEntry(new File(filePath).getParent());
    }

    /**
     * Checks to see if we are at the root directory.  Useful since we are
     * not allow to delete this directory.
     *
     * @param filePath to directory
     * @return true if we are at the root, false otherwise.
     */
    private boolean atRootDirectory(String filePath) {
        filePath = stripFileProtocol(filePath);

        if (filePath.equals(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/" + ctx.getPackageName() + "/cache") ||
                filePath.equals(Environment.getExternalStorageDirectory().getAbsolutePath()) || 
                filePath.equals("/data/data/" + ctx.getPackageName())) {
            return true;
        }
        return false;
    }

    /**
     * This method removes the "file://" from the passed in filePath
     * 
     * @param filePath to be checked.
     * @return
     */
    private String stripFileProtocol(String filePath) {
        if (filePath.startsWith("file://")) {
            filePath = filePath.substring(7);
        }
        return filePath;
    }
    
    /**
     * Create a File object from the passed in path
     * 
     * @param filePath
     * @return
     */
    private File createFileObject(String filePath) {
        filePath = stripFileProtocol(filePath);

        File file = new File(filePath);
        return file;
    }

    /**
     * Look up metadata about this entry.
     *
     * @param filePath to entry
     * @return a Metadata object
     * @throws FileNotFoundException
     * @throws JSONException
     */
    private JSONObject getMetadata(String filePath) throws FileNotFoundException, JSONException {
        File file = createFileObject(filePath);

        if (!file.exists()) {
            throw new FileNotFoundException("Failed to find file in getMetadata");
        }

        JSONObject metadata = new JSONObject();
        metadata.put("modificationTime", file.lastModified());

        return metadata;
    }

    /**
     * Returns a File that represents the current state of the file that this FileEntry represents.
     *
     * @param filePath to entry
     * @return returns a JSONObject represent a W3C File object
     * @throws FileNotFoundException
     * @throws JSONException
     */
    private JSONObject getFileMetadata(String filePath) throws FileNotFoundException, JSONException {
        File file = createFileObject(filePath);

        if (!file.exists()) {
            throw new FileNotFoundException("File: " + filePath + " does not exist.");
        }

         JSONObject metadata = new JSONObject();
        metadata.put("size", file.length());
        metadata.put("type", getMimeType(filePath));
        metadata.put("name", file.getName());
        metadata.put("fullPath", file.getAbsolutePath());
        metadata.put("lastModifiedDate", file.lastModified());

        return metadata;
    }

    /**
     * Requests a filesystem in which to store application data.
     *
     * @param type of file system requested
     * @return a JSONObject representing the file system
     * @throws IOException
     * @throws JSONException
     */
    private JSONObject requestFileSystem(int type) throws IOException, JSONException {
        JSONObject fs = new JSONObject();
        if (type == TEMPORARY) {
            File fp;
            fs.put("name", "temporary");
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                fp = new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                    "/Android/data/" + ctx.getPackageName() + "/cache/");
                // Create the cache dir if it doesn't exist.
                fp.mkdirs();
                fs.put("root", getEntry(Environment.getExternalStorageDirectory().getAbsolutePath() +
                        "/Android/data/" + ctx.getPackageName() + "/cache/"));
            } else {
                fp = new File("/data/data/" + ctx.getPackageName() + "/cache/");
                // Create the cache dir if it doesn't exist.
                fp.mkdirs();
                fs.put("root", getEntry("/data/data/" + ctx.getPackageName() + "/cache/"));
            }
        }
        else if (type == PERSISTENT) {
            fs.put("name", "persistent");
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                fs.put("root", getEntry(Environment.getExternalStorageDirectory()));
            } else {
                fs.put("root", getEntry("/data/data/" + ctx.getPackageName()));
            }
        }
        else {
            throw new IOException("No filesystem of type requested");
        }
 
        return fs;
    }

    /**
     * Returns a JSON Object representing a directory on the device's file system
     *
     * @param path to the directory
     * @return
     * @throws JSONException
     */
    public JSONObject getEntry(File file) throws JSONException {
        JSONObject entry = new JSONObject();

        entry.put("isFile", file.isFile());
        entry.put("isDirectory", file.isDirectory());
        entry.put("name", file.getName());
        entry.put("fullPath", "file://" + file.getAbsolutePath());
        // I can't add the next thing it as it would be an infinite loop
        //entry.put("filesystem", null);

        return entry;
    }

    /**
     * Returns a JSON Object representing a directory on the device's file system
     *
     * @param path to the directory
     * @return
     * @throws JSONException
     */
    private JSONObject getEntry(String path) throws JSONException {
        return getEntry(new File(path));
    }

    /**
     * Identifies if action to be executed returns a value and should be run synchronously.
     *
     * @param action	The action to execute
     * @return			T=returns value
     */
    public boolean isSynch(String action) {
        if (action.equals("testSaveLocationExists")) {
            return true;
        }
        else if (action.equals("getFreeDiskSpace")) {
            return true;
        }
        else if (action.equals("testFileExists")) {
            return true;
        }
        else if (action.equals("testDirectoryExists")) {
            return true;
        }
        return false;
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    /**
     * Read content of text file.
     *
     * @param filename			The name of the file.
     * @param encoding			The encoding to return contents as.  Typical value is UTF-8.
     * 							(see http://www.iana.org/assignments/character-sets)
     * @return					Contents of file.
     * @throws FileNotFoundException, IOException
     */
    public String readAsText(String filename, String encoding) throws FileNotFoundException, IOException {
        byte[] bytes = new byte[1000];
        BufferedInputStream bis = new BufferedInputStream(getPathFromUri(filename), 1024);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int numRead = 0;
        while ((numRead = bis.read(bytes, 0, 1000)) >= 0) {
            bos.write(bytes, 0, numRead);
        }
        return new String(bos.toByteArray(), encoding);
    }

    /**
     * Read content of text file and return as base64 encoded data url.
     *
     * @param filename			The name of the file.
     * @return					Contents of file = data:<media type>;base64,<data>
     * @throws FileNotFoundException, IOException
     */
    public String readAsDataURL(String filename) throws FileNotFoundException, IOException {
        byte[] bytes = new byte[1000];
        BufferedInputStream bis = new BufferedInputStream(getPathFromUri(filename), 1024);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int numRead = 0;
        while ((numRead = bis.read(bytes, 0, 1000)) >= 0) {
            bos.write(bytes, 0, numRead);
        }

        // Determine content type from file name
        String contentType = null;
        if (filename.startsWith("content:")) {
            Uri fileUri = Uri.parse(filename);
            contentType = this.ctx.getContentResolver().getType(fileUri);
        }
        else {
            contentType = getMimeType(filename);
        }

        byte[] base64 = Base64.encodeBase64(bos.toByteArray());
        String data = "data:" + contentType + ";base64," + new String(base64);
        return data;
    }

    /**
     * Looks up the mime type of a given file name.
     *
     * @param filename
     * @return a mime type
     */
    public static String getMimeType(String filename) {
        MimeTypeMap map = MimeTypeMap.getSingleton();
        return map.getMimeTypeFromExtension(map.getFileExtensionFromUrl(filename));
    }

    /**
     * Write contents of file.
     *
     * @param filename			The name of the file.
     * @param data				The contents of the file.
     * @param offset			The position to begin writing the file.
     * @throws FileNotFoundException, IOException
     */
    /**/
    public long write(String filename, String data, int offset) throws FileNotFoundException, IOException {
        filename = stripFileProtocol(filename);

        boolean append = false;
        if (offset > 0) {
            this.truncateFile(filename, offset);
            append = true;
        }

       byte [] rawData = data.getBytes();
       ByteArrayInputStream in = new ByteArrayInputStream(rawData);
       FileOutputStream out = new FileOutputStream(filename, append);
       byte buff[] = new byte[rawData.length];
       in.read(buff, 0, buff.length);
       out.write(buff, 0, rawData.length);
       out.flush();
       out.close();

        return data.length();
    }

    /**
     * Truncate the file to size
     *
     * @param filename
     * @param size
     * @throws FileNotFoundException, IOException
     */
    private long truncateFile(String filename, long size) throws FileNotFoundException, IOException {
        filename = stripFileProtocol(filename);

        RandomAccessFile raf = new RandomAccessFile(filename, "rw");

        if (raf.length() >= size) {
               FileChannel channel = raf.getChannel();
               channel.truncate(size);
               return size;
        }

        return raf.length();
    }

    /**
     * Get an input stream based on file path or content:// uri
     *
     * @param path
     * @return an input stream
     * @throws FileNotFoundException
     */
    private InputStream getPathFromUri(String path) throws FileNotFoundException {
        if (path.startsWith("content")) {
            Uri uri = Uri.parse(path);
            return ctx.getContentResolver().openInputStream(uri);
        }
        else {
            path = stripFileProtocol(path);
            return new FileInputStream(path);
        }
    }

    /**
     * Queries the media store to find out what the file path is for the Uri we supply
     *
     * @param contentUri the Uri of the audio/image/video
     * @param ctx the current applicaiton context
     * @return the full path to the file
     */
    protected static String getRealPathFromURI(Uri contentUri, CordovaInterface ctx) {
        String[] proj = { _DATA };
        Cursor cursor = ctx.managedQuery(contentUri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(_DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }
}
