/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imagina.servidoremapasc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;

/**
 *
 * @author jairo
 */
public class ServidorDescargarCargar extends HttpServlet {

    String inicioArchivo;
    ServletFileUpload uploader;

    public ServidorDescargarCargar() {
        String appBase = ".";
        File file = new File(appBase);
        try {
            this.inicioArchivo = file.getCanonicalPath() + "/Archivos";
        } catch (IOException ex) {
            Logger.getLogger(ServidorDescargarCargar.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    @Override
    public void init() throws ServletException {
        DiskFileItemFactory fileFactory = new DiskFileItemFactory();
        fileFactory.setRepository(new File(inicioArchivo));
        this.uploader = new ServletFileUpload(fileFactory);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        revisarArchivos(new File(inicioArchivo));
        boolean descargado = false;
        String archivo = request.getParameter("nombreArchivo");
        if (archivo != null && !archivo.isEmpty()) {
            String[] i = archivo.split("/");
            File file = construir(i);
            if (file != null && file.exists()) {
                descargado = true;
                if (file.isDirectory()) {
                    html(response.getWriter(), enlace(i), file.listFiles());
                } else {
                    ServletContext ctx = getServletContext();
                    try (InputStream fis = new FileInputStream(file)) {
                        String mimeType = ctx.getMimeType(file.getAbsolutePath());
                        response.setContentType(mimeType != null ? mimeType : "application/octet-stream");
                        response.setContentLength((int) file.length());
                        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
                        try (ServletOutputStream os = response.getOutputStream()) {
                            byte[] bufferData = new byte[1024];
                            int read = 0;
                            while ((read = fis.read(bufferData)) != -1) {
                                os.write(bufferData, 0, read);
                            }
                            os.flush();
                        }
                        escribirDatos(new GregorianCalendar(), file, request.getRemoteAddr());
                    }
                }
            }
        }
        if (!descargado) {
            html(response.getWriter(), "", new File(inicioArchivo).listFiles());
        }
    }

    @Override
    public void destroy() {
        System.out.println("Destruye");
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        final PrintWriter out = response.getWriter();
        if (!ServletFileUpload.isMultipartContent(request)) {
            throw new ServletException("Content type is not multipart/form-data");
        }
        response.setContentType("text/html");
        String msg = "Primero cargue un archivo, no sea ... ";
        try {
            Map<String, List<FileItem>> map = uploader.parseParameterMap(request);
            Iterator<String> iterator = map.keySet().iterator();
            while (iterator.hasNext()) {
                List<FileItem> fileItemsList = map.get(iterator.next());
                Iterator<FileItem> fileItemsIterator = fileItemsList.iterator();
                while (fileItemsIterator.hasNext()) {
                    FileItem fileItem = fileItemsIterator.next();
                    if (fileItem.getSize() != 0) {
                        String archivo = request.getParameter("nombreArchivo");
                        String ruta = inicioArchivo + File.separator;
                        if (archivo != null && !archivo.isEmpty()) {
                            String[] i = archivo.split("/");
                            ruta = construir(i).getPath();
                        }
                        if (new File(ruta).exists()) {
                            File file = new File(ruta + File.separator + modificar(quitarEspeciales(fileItem.getName()).split(" ")));
                            fileItem.write(file);
                            msg = "Archivo " + fileItem.getName() + " cargado exitosamente. En la carpeta " + new File(ruta).getName() + ".";
                        } else {
                            msg = "La carpeta de destino, no existe.";
                        }
                    }
                }
            }
        } catch (Exception e) {
            msg = "Error al subir. Intente de nuevo";
        }
        out.write("<html><head></head><body>");
        out.write(msg);
        out.write("</body></html>");
    }

    public void html(PrintWriter pw, String enlace, File[] files) {
        try (PrintWriter out = pw) {
            out.println("<html>");
            out.println("<head><h5>Es posible que archivos mayores a 300 MB no puedan ser cargados</h5></head>");
            out.println("<hr>");
            out.println("<title>KAALA</title>");
            out.println("<body>");
            out.println("<form method=\"post\" enctype=\"multipart/form-data\">");
            out.println("SUBIR ARCHIVO: <input type=\"file\" name=\"fileName\">");
            out.println("<input type=\"submit\" value=\"SUBIR\">");
            out.println("</form>");
            for (File file : files) {
                out.write("<br>");
                String name = file.getName();
                out.write("<a href=?nombreArchivo=" + enlace + modificar(name.split(" ")) + ">" + name + "</a>");
            }
            out.println("</body>");
            out.println("</hr>");
            out.println("</html>");
        }
    }

    private File construir(String[] i) {
        File f = buscar(new File(inicioArchivo).listFiles(), i[0]);
        for (int j = 1; j < i.length; j++) {
            f = buscar(f.listFiles(), i[j]);
        }
        return f;
    }

    private String enlace(String[] i) {
        String link = i[0] + "/";
        for (int j = 1; j < i.length; j++) {
            link += i[j] + "/";
        }
        return link;
    }

    private void escribirDatos(Calendar c, File file, String ip) {
        try {
            try (FileWriter fichero = new FileWriter(new File(inicioArchivo).getParent() + "/sesiones.txt", new File(new File(inicioArchivo).getParent() + "/sesiones.txt").exists())) {
                PrintWriter pw = new PrintWriter(fichero);
                pw.println(ip + " ha descargado el archivo " + file.getName() + " de la carpeta " + file.getParent()
                        + ". Fecha: " + String.valueOf(c.get(Calendar.DAY_OF_MONTH)) + "-" + String.valueOf(c.get(Calendar.MONTH) + 1)
                        + "-" + String.valueOf(c.get(Calendar.YEAR)) + " " + String.valueOf(c.get(Calendar.HOUR_OF_DAY)) + ":" + analizar(c.get(Calendar.MINUTE))
                        + ":" + analizar(c.get(Calendar.SECOND)));
            }
        } catch (IOException ex) {
            System.out.println(ex.toString());
        }
    }

    private String analizar(int num) {
        if (num < 10) {
            return "0" + String.valueOf(num);
        }
        return String.valueOf(num);
    }

    private File buscar(File[] files, String i) {
        for (File file : files) {
            if (file.getName().equalsIgnoreCase(i)) {
                return file;
            }
        }
        return null;
    }

    private String modificar(String[] split) {
        if (split.length > 1) {
            for (int i = 1; i < split.length; i++) {
                split[0] += "_" + split[i];
            }
        }
        return split[0];
    }

    private String quitarEspeciales(String name) {
        String n = name.toLowerCase();
        String nuevo = "";
        String vocales = "aeiou";
        String vowels = "áéíóúäëïöü";
        for (int i = 0; i < n.length(); i++) {
            int p = -1;
            int l = n.charAt(i);
            for (int j = 0; j < vowels.length(); j++) {
                int v = vowels.charAt(j);
                if (v == l) {
                    p = j;
                }
            }
            if (p == -1) {
                nuevo += String.valueOf(name.charAt(i));
            } else {
                if (p > 4) {
                    p = p - 5;
                }
                nuevo += String.valueOf(vocales.charAt(p));
            }
        }
        return nuevo;
    }

    private void revisarArchivos(File file) {
        File[] f = file.listFiles();
        for (File f1 : f) {
            if (f1.isDirectory()) {
                revisarArchivos(f1);
            } else {
                String name = f1.getName();
                if (name.contains(" ")) {
                    String name2 = f1.getParent() + "/" + modificar(quitarEspeciales(name).split(" "));
                    f1.renameTo(new File(name2));
                }
            }
        }
    }

}
