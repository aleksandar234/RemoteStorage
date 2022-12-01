package org.raf.googleApi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import org.mortbay.util.IO;
import org.raf.specification.Specification;

import com.google.api.services.drive.model.File;
import org.raf.storage.JsonConfig;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class RemoteStorage implements Specification {

    /**
     * Application name.
     */
    private static final String APPLICATION_NAME = "MyProject";

    /**
     * Global instance of the {@link FileDataStoreFactory}.
     */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /**
     * Global instance of the JSON factory.
     */
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();


    private static HttpTransport HTTP_TRANSPORT;

    /**
     * Global instance of the scopes required by this quickstart.
     * <p>
     * If modifying these scopes, delete your previously saved credentials at
     * ~/.credentials/calendar-java-quickstart
     */
    private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE);

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }


    public static Credential authorize() throws IOException {
        // Load client secrets.
        InputStream in = RemoteStorage.class.getResourceAsStream("/client_secret1.json");
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
                clientSecrets, SCOPES).setAccessType("offline").build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
        return credential;
    }

    /**
     * Build and return an authorized Calendar client service.
     *
     * @return an authorized Calendar client service
     * @throws IOException
     */
    public static Drive getDriveService() throws IOException {
        Credential credential = authorize();
        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private final String storagePath;
    private static Drive service = null;
    private ObjectMapper objectMapper = new ObjectMapper();

    public RemoteStorage(String storagePath) {
        this.storagePath = storagePath;
    }

//    public static void main(String[] args) {
//        RemoteStorage remoteStorage = new RemoteStorage("DarkShadow");

//        remoteStorage.delete("Dir1\\hhhhh.json");
//        remoteStorage.upload("C:\\Users\\Korisnik\\Desktop\\UploadRemote.txt","NewDir\\");
//    remoteStorage.download("Dir2\\aaa.txt", "C:\\Users\\Korisnik\\Desktop");

//        remoteStorage.init();
//        remoteStorage.getAllFilesInDir("DarkShadow");
//        remoteStorage.getAllFilesFromAllDirsInDir("DarkShadow");
//        remoteStorage.getFilesWithExtension("DarkShadow", "txt");
//        remoteStorage.getFilesWithSubstring("DarkShadow", "c");
//        remoteStorage.getFilesWithName("DarkShadow", "ccc.json","najjaciSam.json","coonfig.json");
//        remoteStorage.getDirFromNameOfFile("hhhhh.json");
//        remoteStorage.getAllFilesSortedByName("DarkShadow", "descending");
//        System.out.println("-------------------");
//        remoteStorage.getAllFilesSortedByName("DarkShadow", "ascending");
//        remoteStorage.getAllFilesSortedByDate("DarkShadow", "ascending");
//        remoteStorage.getFilesCreatedInCertainTime("DarkShadow", "2022-11-24");
//    }


    public String getId(String file) throws IOException {
        ArrayList<File> directories = new ArrayList<>();
        String pageToken = null;
        String dirID = null;
        do{
            FileList result = service.files().list()
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name)")
                    .setPageToken(pageToken)
                    .execute();
            List<File> filtered = result.getFiles().stream()
                    .filter(file1 -> file1.getName().equals(file))
                    .collect(Collectors.toList());
            directories.addAll(filtered);
            pageToken = result.getNextPageToken();

        } while(pageToken != null);

        for(File directory: directories) {
            dirID = directory.getId();
            break;
        }
        return dirID;
    }

    private String getFileId(String path) throws IOException {
        if(path == null)
            throw new IllegalArgumentException("Path must not be null");

        String[] directories = path.split("\\\\");
        String fileName = directories[directories.length-1];
        String[] allDirectories = new String[directories.length-1];
        System.arraycopy(directories, 0, allDirectories, 0, allDirectories.length);
        String fileId = searchFileInFolder(fileName, allDirectories);

        return fileId;
    }

    private String searchFileInFolder(String fileName, String[] allDirectories) throws IOException {

        if(allDirectories == null || allDirectories.length == 0)
            throw new IllegalArgumentException("Must have at least root directory as parent");
        // vraca mi poslednji folderId, ako ne onda mi vraca null
        String dirId = searchAllDirectories(allDirectories);

        if(dirId == null)
            throw new IllegalArgumentException("Directory does not exist");

        List<File> files = new ArrayList<>();
        String pageToken = null;
        do {
            FileList result = service.files().list()
                    .setQ("mimeType != 'application/vnd.google-apps.folder'")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, parents)")
                    .setPageToken(pageToken)
                    .execute();
            files.addAll(result.getFiles());
            pageToken = result.getNextPageToken();
        } while (pageToken != null);


        return files.stream()
                .filter(f -> f.getName().equals(fileName) && f.getParents().contains(dirId))
                .findAny()
                .orElseThrow(() -> new RuntimeException("No such file on specified path"))
                .getId();
    }


    private String searchAllDirectories(String[] allDirectories) throws IOException {

        String root = allDirectories[0];
        if(!root.equals(storagePath))
            throw new IllegalArgumentException("Invalid storage path");

        String dirId = getId(root);

        List<File> directories = new ArrayList<>();
        String pageToken = null;
        do {
            FileList result = service.files().list()
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, parents)")
                    .setPageToken(pageToken)
                    .execute();
            directories.addAll(result.getFiles());
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        outer:
        for(int i = 1; i < allDirectories.length; i++) {
            String directoryName = allDirectories[i];
            List<File> filtered = directories.stream()
                    .filter(f -> f.getName().equals(directoryName))
                    .collect(Collectors.toList());

            for(File dir: filtered) {
                boolean parentFound = dir.getParents()
                        .stream()
                        .anyMatch(dirId::equals);
                if(parentFound) {
                    dirId = dir.getId();
                    continue outer;
                }
            }
            return null;
        }

        return dirId;

    }


    public void createFileInRootFolder(String name) {
        String rootFolder = storagePath;
        try {
            String rootId = getId(rootFolder);
            File fileMetaData = new File();
            fileMetaData.setName(name);
            fileMetaData.setParents(Collections.singletonList(rootId));
            fileMetaData.setMimeType("application/octet-stream");
            fileMetaData.setKind("drive.kind");
            service.files().create(fileMetaData)
                    .setFields("id")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String createDirectories(String[] parentDirectories) throws IOException {
        String root = parentDirectories[0];
        if(!root.equals(storagePath))
            throw new IllegalArgumentException("Invalid storage root: " + root);

        String dirId = getId(root);
        outer:
        for(int i = 1; i < parentDirectories.length; i++) {
            String dirName = parentDirectories[i];
            String pageToken = null;
            List<File> directories = new ArrayList<>();
            do {
                FileList result = service.files().list()
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name, parents)")
                        .setPageToken(pageToken)
                        .execute();
                List<File> filtered = result.getFiles().stream()
                        .filter(file -> file.getName().equals(dirName))
                        .collect(Collectors.toList());
                directories.addAll(filtered);
                pageToken = result.getNextPageToken();
            } while (pageToken != null);
            for(File directory: directories) {
                List<String> parents = directory.getParents();
                if(parents.contains(dirId)) {
                    dirId = directory.getId();
                    continue outer;
                }
            }

            File fileMetadata = new File();
            fileMetadata.setName(parentDirectories[i]);
            fileMetadata.setMimeType("application/vnd.google-apps.folder");
            fileMetadata.setParents(Collections.singletonList(dirId));

            File file = service.files().create(fileMetadata)
                    .setFields("id")
                    .execute();
            dirId = file.getId();
        }

        return dirId;
    }

    @Override
    public void init() {

        List<File> files = new ArrayList<>();
        String pageToken = null;
        do {
            FileList result = null;
            try {
                service = getDriveService();
                result = service.files().list()
                        .setQ("mimeType = 'application/vnd.google-apps.folder'")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name)")
                        .setPageToken(pageToken)
                        .execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
            files.addAll(result.getFiles());
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        List<File> allFolders = files.stream()
                .filter(f -> f.getName().equals(storagePath))
                .collect(Collectors.toList());

        if(allFolders.size() == 0) {
            File fileMetaData = new File();
            fileMetaData.setName(storagePath);
            fileMetaData.setMimeType("application/vnd.google-apps.folder");
            try {
                service.files().create(fileMetaData)
                        .setFields("id")
                        .execute();
                createFileInRootFolder("config.json");
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        File path = new File();
        for(File f: allFolders) {
            path = f;
            break;
        }
        String ppath = path.getName();

        try {
            String storageId = getId(ppath);
            List<File> ffiles = new ArrayList<>();
            pageToken = null;
            do {
                FileList result = null;
                try {
                    result = service.files().list()
                            .setQ("mimeType != 'application/vnd.google-apps.folder'")
                            .setSpaces("drive")
                            .setFields("nextPageToken, files(id, name, parents)")
                            .setPageToken(pageToken)
                            .execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ffiles.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
            } while (pageToken != null);

            List<File> filesList = new ArrayList<>();
            for (File file: ffiles) {
                if(file.getParents() != null) {
                    filesList.add(file);
                }
            }

            List<File> allFiles = filesList.stream()
                    .filter(f -> f.getParents().contains(storageId) && f.getName().equals("config.json"))
                    .collect(Collectors.toList());

            if(allFiles.size() > 0) {
                System.out.println("Path already exists");
                return;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    /**
     * Making Specified amount of dirFiles in rootFolder
     * @param size
     */

    @Override
    public void makeDirs(int size) {
        String rootFolder = storagePath;
        try {
            service = getDriveService();
        } catch (IOException e) {
            e.printStackTrace();
        }
        for(int i = 0; i < size; i++) {
            try {
                String rootId = getId(rootFolder);
                File fileMetaData = new File();
                fileMetaData.setMimeType("application/vnd.google-apps.folder");
                fileMetaData.setParents(Collections.singletonList(rootId));
                fileMetaData.setName("Dir" + (i+1));
                try {
                    File file = service.files().create(fileMetaData)
                            .setFields("id")
                            .execute();
                    System.out.println("FolderID: " + file.getId());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void setMaxFileSize(int i) {

    }

    @Override
    public void setBlockedExtensions(List<String> list) {

    }

    @Override
    public void download(String remotePath, String localPath) {
        try {
            service = getDriveService();
            String fileId = getFileId(storagePath + "\\" + remotePath);
            String[] tokens = remotePath.split("\\\\");
            String fileName = tokens[tokens.length-1];
            OutputStream outputStream = new FileOutputStream(localPath + java.io.File.separator + fileName);

            service.files().get(fileId)
                    .executeMediaAndDownloadTo(outputStream);

            service = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void upload(String localPath, String remotePath) {
        try {
            service = getDriveService();
            String folderId = createDirectories((storagePath + "\\" + remotePath).split("\\\\"));
            java.io.File file = new java.io.File(localPath);
            String fileName = file.getName();
            File fileMetaData = new File();
            fileMetaData.setName(fileName);
            fileMetaData.setParents(Collections.singletonList(folderId));
            FileContent mediaContent = new FileContent(fileMetaData.getMimeType(), file);
            service.files().create(fileMetaData, mediaContent)
                    .setFields("id")
                    .execute();
            service = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void rename(String oldName, String newName) {

    }

    /**
     * Kreiranje fajla u korenskom folderu
     * @param s
     */
    @Override
    public void createFile(String s) {
        String rootFolder = storagePath;
        try {
            String rootId = getId(rootFolder);
            File fileMetaData = new File();
            fileMetaData.setName(s);
            fileMetaData.setParents(Collections.singletonList(rootId));
            fileMetaData.setMimeType("application/octet-stream");
            fileMetaData.setKind("drive.kind");
            service.files().create(fileMetaData)
                    .setFields("id")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void delete(String s) {
        try {
            service = getDriveService();
            String fileId;
            fileId = getFileId(storagePath + java.io.File.separator + s);
            service.files().delete(fileId).execute();
            service = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String gettingIdOfLastDir(String dirPath) {
        String[] directories = dirPath.split("\\\\");
        String fileName = directories[directories.length-1];
        String[] allDirectories = new String[directories.length];
        System.arraycopy(directories, 0, allDirectories, 0, allDirectories.length);
        try {
            service = getDriveService();
            String dirId = searchAllDirectories(allDirectories);
            return dirId;
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Directory does not exists");
        return null;
    }

    @Override
    public void getAllFilesInDir(String dirName) {
        try {
            service = getDriveService();
            String dirID = gettingIdOfLastDir(dirName);
            List<File> files = new ArrayList<>();
            String pageToken = null;
            do {
                FileList result = service.files().list()
                        .setQ("mimeType != 'application/vnd.google-apps.folder'")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name, parents)")
                        .setPageToken(pageToken)
                        .execute();
                files.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
            } while (pageToken != null);
            List<File> ffs = new ArrayList<>();
            for (File filess: files) {
                if(filess.getParents() != null) {
                    ffs.add(filess);
                }
            }
            ffs.stream()
                    .filter(f -> f.getParents().contains(dirID))
                    .forEach((file) -> System.out.println(file.getName()));


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void recursiveCallForFiles(String dirId) throws IOException {
        List<File> files = new ArrayList<>();
        String pageToken = null;
        do {
            FileList result = service.files().list()
                    .setQ("mimeType != 'application/vnd.google-apps.folder'")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, parents)")
                    .setPageToken(pageToken)
                    .execute();
            files.addAll(result.getFiles());
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        List<File> ffs = new ArrayList<>();
        for (File filess: files) {
            if(filess.getParents() != null) {
                ffs.add(filess);
            }
        }

        List<File> allFiles = ffs.stream()
                .filter(f -> f.getParents().contains(dirId))
                .collect(Collectors.toList());

        for(File file: allFiles) {
            System.out.println(file.getName());
        }

        pageToken = null;
        do {
            FileList result = service.files().list()
                    .setQ("mimeType = 'application/vnd.google-apps.folder'")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, parents)")
                    .setPageToken(pageToken)
                    .execute();
            files.addAll(result.getFiles());
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        List<File> fs = new ArrayList<>();
        for (File fis: files) {
            if(fis.getParents() != null) {
                fs.add(fis);
            }
        }

        List<File> allDirs = fs.stream()
                .filter(f -> f.getParents().contains(dirId))
                .collect(Collectors.toList());

        for(File dir: allDirs) {
            recursiveCallForFiles(dir.getId());
        }

    }

    @Override
    public void getAllFilesFromAllDirsInDir(String dirPath) {
        String[] directories = dirPath.split("\\\\");
        String[] allDirectories = new String[directories.length];
        System.arraycopy(directories, 0, allDirectories, 0, allDirectories.length);
        try {
            service = getDriveService();
            String dirId = searchAllDirectories(allDirectories);

            List<File> files = new ArrayList<>();
            String pageToken = null;
            do {
                FileList result = service.files().list()
                        .setQ("mimeType != 'application/vnd.google-apps.folder'")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name, parents)")
                        .setPageToken(pageToken)
                        .execute();
                files.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
            } while (pageToken != null);

            List<File> listFiles = new ArrayList<>();
            for (File filess: files) {
                if(filess.getParents() != null) {
                    listFiles.add(filess);
                }
            }

            List<File> allFiles = listFiles.stream()
                    .filter(f -> f.getParents().contains(dirId))
                    .collect(Collectors.toList());

            for(File f: allFiles) {
                System.out.println(f.getName());
            }

            // Sve fajlove ispisi
            //--------------------------------------------------

            List<File> dirs = new ArrayList<>();
            pageToken = null;
            do {
                FileList result = service.files().list()
                        .setQ("mimeType = 'application/vnd.google-apps.folder'")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name, parents)")
                        .setPageToken(pageToken)
                        .execute();
                dirs.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
            } while (pageToken != null);

            List<File> listDirectories = new ArrayList<>();
            for (File directory: dirs) {
                if(directory.getParents() != null) {
                    listDirectories.add(directory);
                }
            }



            List<File> allDirs = listDirectories.stream()
                    .filter(f -> f.getParents().contains(dirId))
                    .collect(Collectors.toList());
           for (File directory: allDirs) {
               recursiveCallForFiles(directory.getId());
           }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    @Override
    public void getFilesWithExtension(String dirPath, String extension) {
        try {
            String[] directories = dirPath.split("\\\\");
            String[] allDirectories = new String[directories.length];
            System.arraycopy(directories, 0, allDirectories, 0, allDirectories.length);
            service = getDriveService();
            String dirId = searchAllDirectories(allDirectories);
            List<File> files = new ArrayList<>();
            String pageToken = null;
            do {
                FileList result = service.files().list()
                        .setQ("mimeType != 'application/vnd.google-apps.folder'")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name, parents)")
                        .setPageToken(pageToken)
                        .execute();
                files.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
            } while (pageToken != null);

            List<File> listFiles = new ArrayList<>();
            for (File file: files) {
                if(file.getParents() != null) {
                    listFiles.add(file);
                }
            }

            List<File> allFiles = listFiles.stream()
                    .filter(f -> f.getParents().contains(dirId))
                    .collect(Collectors.toList());

            for(File f: allFiles) {
                String name = f.getName();
                String ext = name.substring(name.lastIndexOf(".") + 1);
                if(ext.equals(extension)) {
                    System.out.println(name);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void getFilesWithSubstring(String dirPath, String sub) {
        String[] directories = dirPath.split("\\\\");
        String[] allDirectories = new String[directories.length];
        System.arraycopy(directories, 0, allDirectories, 0, allDirectories.length);
        try {
            service = getDriveService();
            String dirId = searchAllDirectories(allDirectories);
            List<File> files = new ArrayList<>();
            String pageToken = null;
            do {
                FileList result = service.files().list()
                        .setQ("mimeType != 'application/vnd.google-apps.folder'")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name, parents)")
                        .setPageToken(pageToken)
                        .execute();
                files.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
            } while (pageToken != null);

            List<File> listFiles = new ArrayList<>();
            for (File file: files) {
                if(file.getParents() != null) {
                    listFiles.add(file);
                }
            }

            List<File> allFiles = listFiles.stream()
                    .filter(f -> f.getParents().contains(dirId))
                    .collect(Collectors.toList());

            for(File file: allFiles) {
                String nameOfFile = file.getName();
                if(nameOfFile.startsWith(sub) || nameOfFile.endsWith(sub) || nameOfFile.contains(sub)) {
                    System.out.println(nameOfFile);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean getFilesWithName(String dirPath, String... fileNames) {

        String[] directories = dirPath.split("\\\\");
        String[] allDirectories = new String[directories.length];
        System.arraycopy(directories, 0, allDirectories, 0, allDirectories.length);
        try {
            service = getDriveService();
            String dirId = searchAllDirectories(allDirectories);
            List<File> files = new ArrayList<>();
            String pageToken = null;
            do {
                FileList result = service.files().list()
                        .setQ("mimeType != 'application/vnd.google-apps.folder'")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name, parents)")
                        .setPageToken(pageToken)
                        .execute();
                files.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
            } while (pageToken != null);

            List<File> listFiles = new ArrayList<>();
            for (File file: files) {
                if(file.getParents() != null) {
                    listFiles.add(file);
                }
            }

            List<File> allFilesInDir = listFiles.stream()
                    .filter(f -> f.getParents().contains(dirId))
                    .collect(Collectors.toList());
            List<String> allNamesInDir = new ArrayList<>();
            for(File fs: allFilesInDir) {
                allNamesInDir.add(fs.getName());
            }

            List<String> names = Arrays.asList(fileNames);
            Boolean[] contains = new Boolean[fileNames.length];
            int i = 0;
            for(String name: names) {
                if(allNamesInDir.contains(name)) {
                    contains[i++] = true;
                } else {
                    contains[i++] = false;
                }
            }

            System.out.println(Arrays.toString(contains));
            for(Boolean b: contains) {
                if(!b.equals(true)) {
                    return false;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public boolean getDirFromNameOfFile(String fileName) {
        try {
            service = getDriveService();
            String rootId = getId(storagePath);
            List<File> files = new ArrayList<>();
            String pageToken = null;
            do {
                FileList result = service.files().list()
                        .setQ("mimeType != 'application/vnd.google-apps.folder'")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name, parents)")
                        .setPageToken(pageToken)
                        .execute();
                files.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
            } while (pageToken != null);

            List<File> listFiles = new ArrayList<>();
            for (File file: files) {
                if(file.getParents() != null) {
                    listFiles.add(file);
                }
            }

            List<File> allFiles = listFiles.stream()
                    .filter(f -> f.getParents().contains(rootId) && f.getName().equals(fileName))
                    .collect(Collectors.toList());

            if(allFiles.size() > 0) {
                System.out.println("Parent of this file is: " + storagePath);
                return true;
            }

            // Ovo je bilo u slucaju da mi se fajl nalazi u root folderu
            //-------------------------------
            // A sad ako se nalazi unutar nekih drugih foldera

            List<File> directories = new ArrayList<>();
            pageToken = null;
            do {
                FileList result = service.files().list()
                        .setQ("mimeType = 'application/vnd.google-apps.folder'")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name, parents)")
                        .setPageToken(pageToken)
                        .execute();
                directories.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
            } while (pageToken != null);

            List<File> listOfFiles = new ArrayList<>();
            for (File file: directories) {
                if(file.getParents() != null) {
                    listOfFiles.add(file);
                }
            }

            List<File> allDirs = listOfFiles.stream()
                    .filter(f -> f.getParents().contains(rootId))
                    .collect(Collectors.toList());

            for(File dir: allDirs) {
                System.out.println("Entering in dir: " + dir.getName());
                recursiveCall(dir.getName(), dir.getId(), fileName);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void recursiveCall(String dirName, String dirId, String fileName) throws IOException {
        List<File> files = new ArrayList<>();
        String pageToken = null;
        do {
            FileList result = service.files().list()
                    .setQ("mimeType != 'application/vnd.google-apps.folder'")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, parents)")
                    .setPageToken(pageToken)
                    .execute();
            files.addAll(result.getFiles());
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        List<File> listFiles = new ArrayList<>();
        for (File file: files) {
            if(file.getParents() != null) {
                listFiles.add(file);
            }
        }

        List<File> allFiles = listFiles.stream()
                .filter(f -> f.getParents().contains(dirId) && f.getName().equals(fileName))
                .collect(Collectors.toList());

        if(allFiles.size() > 0) {
            System.out.println("Parent of this file is: " + dirName);
            return;
        }

        List<File> directories = new ArrayList<>();
        pageToken = null;
        do {
            FileList result = service.files().list()
                    .setQ("mimeType = 'application/vnd.google-apps.folder'")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, parents)")
                    .setPageToken(pageToken)
                    .execute();
            directories.addAll(result.getFiles());
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        List<File> listOfFiles = new ArrayList<>();
        for (File file: directories) {
            if(file.getParents() != null) {
                listOfFiles.add(file);
            }
        }

        List<File> allDirs = listOfFiles.stream()
                .filter(f -> f.getParents().contains(dirId))
                .collect(Collectors.toList());

        for(File dir: allDirs) {
            System.out.println("Entering in dir: " + dir.getName());
            recursiveCall(dir.getName(), dir.getId(), fileName);
        }

    }

    @Override
    public void getAllFilesSortedByName(String dirPath, String order) {
        String[] directories = dirPath.split("\\\\");
        String[] allDirectories = new String[directories.length];
        System.arraycopy(directories, 0, allDirectories, 0, allDirectories.length);
        try {
            service = getDriveService();
            String dirId = searchAllDirectories(allDirectories);
            List<File> dirs = new ArrayList<>();
            String pageToken = null;
            do {
                FileList result = service.files().list()
                        .setQ("mimeType != 'application/vnd.google-apps.folder'")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name, parents)")
                        .setPageToken(pageToken)
                        .execute();
                dirs.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
            } while (pageToken != null);

            List<File> listOfFiles = new ArrayList<>();
            for (File file: dirs) {
                if(file.getParents() != null) {
                    listOfFiles.add(file);
                }
            }

            List<File> sortedFiles = new ArrayList<>();

            if(order.equals("ascending")) {
                sortedFiles = listOfFiles.stream()
                        .filter(f -> f.getParents().contains(dirId))
                        .sorted(Comparator.comparing(File::getName))
                        .collect(Collectors.toList());
            } else if(order.equals("descending")) {
                sortedFiles = listOfFiles.stream()
                        .filter(f -> f.getParents().contains(dirId))
                        .sorted(Comparator.comparing(File::getName).reversed())
                        .collect(Collectors.toList());
            }

            for(File f: sortedFiles) {
                System.out.println(f.getName());
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void getAllFilesSortedByDate(String dirPath, String order) {
        String[] directories = dirPath.split("\\\\");
        String[] allDirectories = new String[directories.length];
        System.arraycopy(directories, 0, allDirectories, 0, allDirectories.length);
        try {
            service = getDriveService();
            String dirId = searchAllDirectories(allDirectories);
            List<File> dirs = new ArrayList<>();
            String pageToken = null;
            do {
                FileList result = service.files().list()
                        .setQ("mimeType != 'application/vnd.google-apps.folder'")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(createdTime, id, name, parents)")
                        .setPageToken(pageToken)
                        .execute();
                dirs.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
            } while (pageToken != null);

            List<File> listOfFiles = new ArrayList<>();
            for (File file: dirs) {
                if(file.getParents() != null) {
                    listOfFiles.add(file);
                }
            }

            List<File> sortedFiles = listOfFiles.stream()
                            .filter(f -> f.getParents().contains(dirId))
                            .collect(Collectors.toList());

            List<DateTime> dates = new ArrayList<>();
            for(File f: sortedFiles) {
                dates.add(f.getCreatedTime());
            }

            List<DateTime> ds = dates.stream()
                    .sorted(Comparator.comparingLong(DateTime::getValue))
                    .collect(Collectors.toList());

            List<File> newSortedFiles = new ArrayList<>();

            for(DateTime dt: ds) {
                for(File f: sortedFiles) {
                    if(f.getCreatedTime().equals(dt)) {
                        newSortedFiles.add(f);
                        break;
                    }
                }
            }

            if(order.equals("ascending")) {
                for(File f: newSortedFiles) {
                    System.out.println(f.getName());
                }
            } else if(order.equals("descending")) {
                Collections.reverse(newSortedFiles);
                for(File f: newSortedFiles) {
                    System.out.println(f.getName());
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void getFilesCreatedInCertainTime(String dirPath, String date) {
        String[] directories = dirPath.split("\\\\");
        String[] allDirectories = new String[directories.length];
        System.arraycopy(directories, 0, allDirectories, 0, allDirectories.length);
        try {
            service = getDriveService();
            String dirId = searchAllDirectories(allDirectories);
            List<File> dirs = new ArrayList<>();
            String pageToken = null;
            do {
                FileList result = service.files().list()
                        .setQ("mimeType != 'application/vnd.google-apps.folder'")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(createdTime, id, name, parents)")
                        .setPageToken(pageToken)
                        .execute();
                dirs.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
            } while (pageToken != null);

            List<File> listOfFiles = new ArrayList<>();
            for (File file: dirs) {
                if(file.getParents() != null) {
                    listOfFiles.add(file);
                }
            }

            List<File> files = listOfFiles.stream()
                    .filter(f -> f.getParents().contains(dirId))
                    .collect(Collectors.toList());

            List<File> dates = new ArrayList();
            for(File f: files) {
                String created = f.getCreatedTime().toString();
                String[] createdDate = created.split("T");
                if(date.equals(createdDate[0])) {
                    dates.add(f);
                }
            }

            for(File f: dates) {
                System.out.println(f.getName());
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
