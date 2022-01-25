package servidor;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import javax.imageio.ImageIO;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;

/**
 *
 * @author Jairo Cabrera
 */
@WebServlet(urlPatterns = {"/servidor"})
public class ServidorDescargarCargar extends HttpServlet {

    ServletFileUpload uploader;
    String carpeta, alternativa, ayuda;

    public ServidorDescargarCargar() {
        File file = new File(".");
        try {
            String inicioArchivo = file.getCanonicalPath() + "/";
            carpeta = inicioArchivo + "Archivos/";
            alternativa = inicioArchivo + "Escala/";
            ayuda = inicioArchivo + "Escala/Ayuda/";
        } catch (IOException ex) {
            Logger.getLogger(ServidorDescargarCargar.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void init() throws ServletException {
        DiskFileItemFactory fileFactory = new DiskFileItemFactory();
        crearArchivos();
        fileFactory.setRepository(new File(carpeta));
        this.uploader = new ServletFileUpload(fileFactory);
        System.out.println("Depurando archivos");
        depurar(new File(alternativa).listFiles(), new File(carpeta));
        System.out.println("Revisando archivos");
        try {
            revisarArchivos(new File(carpeta).listFiles(), new File(alternativa));
        } catch (IOException ex) {
            Logger.getLogger(ServidorDescargarCargar.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Listo");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        crearArchivos();
        revisarArchivos(new File(carpeta).listFiles(), new File(alternativa));
        String[] data = buscar(request);
        String param = data[0];
        switch (Integer.parseInt(data[1])) {
            case 0:
                File file = construir(param.split("/"));
                if (file != null && file.exists()) {
                    if (file.isDirectory()) {
                        html(response.getWriter(), param + "/", request.getRequestURL().toString(), file.listFiles());
                    } else {
                        String ip = request.getRemoteAddr();
                        System.out.println(ip + "  descargando: " + file.getPath() + " " + fecha(new GregorianCalendar()));
                        long time = System.currentTimeMillis();
                        enviarArchivo(file, response);
                        time = System.currentTimeMillis() - time;
                        if (time == 0) {
                            time = 1;
                        }
                        System.out.println(ip + " descargado: " + file.getName() + "    velocidad: " + (file.length() / time) + " KB/s");
                    }
                }
                break;
            case 1:
                File f = new File(alternativa + param);
                if (f.exists()) {
                    enviarArchivo(f, response);
                } else {
                    enviarArchivo(new File(carpeta + param), response);
                }
                break;
            case 2:
                enviarArchivo(new File(ayuda + param), response);
                break;
            default:
                html(response.getWriter(), "", request.getRequestURL().toString(), new File(carpeta).listFiles());
                break;
        }
    }

    @Override
    public void destroy() {
        System.out.println("Destruye");
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!ServletFileUpload.isMultipartContent(request)) {
            throw new ServletException("Content type is not multipart/form-data");
        }
        String msg = "Primero cargue un archivo, no sea ... ";
        int n = 0;
        String ruta = carpeta;
        try {
            Map<String, List<FileItem>> map = uploader.parseParameterMap(request);
            Iterator<String> iterator = map.keySet().iterator();
            while (iterator.hasNext()) {
                Iterator<FileItem> fileItemsIterator = map.get(iterator.next()).iterator();
                while (fileItemsIterator.hasNext()) {
                    FileItem fileItem = fileItemsIterator.next();
                    if (fileItem.getSize() != 0) {
                        String archivo = request.getParameter("nombreArchivo");
                        if (archivo != null && !archivo.isEmpty()) {
                            ruta = construir(archivo.split("/")).getPath() + "/";
                        }
                        if (new File(ruta).exists()) {
                            int p = fileItem.getName().lastIndexOf(".");
                            String extension = fileItem.getName().substring(p);
                            String nombre = modificar(quitarEspeciales(ruta, fileItem.getName().substring(0, p), extension).split(" "));
                            File file = new File(ruta + nombre + extension);
                            int c = 0;
                            while (file.exists()) {
                                file = new File(ruta + nombre + "_" + c + extension);
                                c++;
                            }
                            fileItem.write(file);
                            n++;
                        } else {
                            msg = "La carpeta de destino, no existe.";
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(ServidorDescargarCargar.class.getName()).log(Level.SEVERE, null, ex);
            msg = "Error al subir. Intente de nuevo";
        }
        if (n > 0) {
            msg = "Se han cargado " + n + " archivos en la carpeta " + new File(ruta).getName() + ".";
        }
        doGet(request, response);
    }

    public void html(PrintWriter pw, String enlace, String ip, File[] files) throws IOException {
        try (PrintWriter out = pw) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"es\">");
            out.println("<head>");
            out.println("<meta charset=\"UTF-8\">");
            out.println("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">");
            out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            out.println("<style>");
            out.println("*{margin: 0;padding: 0;box-sizing: border-box;background: black;}");
            out.println(".saludo{margin-top: 20px;text-align: center;font-size: 50px;color: white;}");
            out.println(".title{color: #30553B}");
            out.println(".letra {margin-top: 50px;margin-bottom: 50px;text-align: center;}");
            out.println(".seleccionar{color: #fff;background: linear-gradient(180deg, #000 0%, #30553B 100%);font-size: 25px;}");
            out.println(".cargar{color: #000;background: #fff;font-size: 25px;}");
            out.println(".article{display: flex;justify-content: space-between;align-items: center;flex-wrap: wrap;}");
            out.println(".archivo{width: 30%;height: auto;overflow: hidden;border-radius: 10px;position: relative;text-align: center;margin-bottom: 20px;text-decoration: none;}");
            out.println(".img{width: 95%;height: 250px;object-fit: cover;border-radius: 10px;}");
            out.println(".nombre{font-size: 16px;color: white;text-align: center;width: 95%;margin: 0 auto;}");
            out.println("@media screen and (max-width:900px) {.seleccionar{font-size: 20px;}.cargar{font-size: 20px;}"
                    + ".article{justify-content: space-evenly;}.archivo{width: 40%;}.article .img{height:200px;}.article .nombre{font-size: 15px}}");
            out.println("@media screen and (max-width:700px) {.seleccionar{font-size: 18px;}.cargar{font-size: 18px;}"
                    + ".article .img{height:150px;}.article .nombre{font-size:12px}}");
            out.println("@media screen and (max-width:400px) {.seleccionar{font-size: 12px;}.cargar{font-size: 12px;}}");
            out.println("</style>");
            out.println("<title>SERVIDOR</title></head>");
            out.println("<body>");
            out.println("<header><h2 class=\"saludo\">Mi servidor</h2>");
            out.println("<h2 class=\"title saludo\">'el poderoso'</h2></header>");
            out.println("<form method=post enctype=multipart/form-data class=\"letra\">");
            out.println("<input class=\"seleccionar\" type=file name=fileName multiple/>");
            out.println("<input class=\"cargar\" type=submit value=SUBIR></form>");
            out.println("<section class=\"services\">");
            out.println("<div class=\"container\"><article class=\"article\">");
            for (File file : files) {
                String name = file.getName();
                out.println("<a class=\"archivo\" href=?nombreArchivo=" + enlace + name + ">");
                out.println("<img src=" + buscarImagen(ip, enlace, name) + " class=\"img\">");
                out.println("<h2 class=\"nombre\">" + name + "</h2><a/>");
            }
            out.println("</article></div></section></body></html>");
        }
    }

    private File construir(String[] i) {
        File f = buscar(new File(carpeta).listFiles(), i[0]);
        for (int j = 1; j < i.length; j++) {
            f = buscar(f.listFiles(), i[j]);
        }
        return f;
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

    private String quitarEspeciales(String ruta, String name, String ext) {
        String n = name.toLowerCase();
        String nuevo = "";
        char c = "0".charAt(0);
        char nu = "9".charAt(0);
        char a = "a".charAt(0);
        char z = "z".charAt(0);
        for (int i = 0; i < n.length(); i++) {
            char l = n.charAt(i);
            if ((l >= c && nu > l) || (l >= a && z > l)) {
                nuevo += String.valueOf(l);
            }
        }
        if (nuevo.isEmpty()) {
            nuevo = "ARCHIVO_SIN_NOMBRE" + ext;
            int con = -1;
            while (new File(ruta + nuevo).exists()) {
                con++;
                nuevo = "ARCHIVO_SIN_NOMBRE_" + con + ext;
            }
        }
        return nuevo;
    }

    private void enviarArchivo(File file, HttpServletResponse response) throws FileNotFoundException, IOException {
        if (file.exists() && !file.isDirectory()) {
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
            }
        }
    }

    private String fecha(Calendar c) {
        return "Fecha: " + String.valueOf(c.get(Calendar.DAY_OF_MONTH)) + "-" + String.valueOf(c.get(Calendar.MONTH) + 1)
                + "-" + String.valueOf(c.get(Calendar.YEAR)) + " " + String.valueOf(c.get(Calendar.HOUR_OF_DAY)) + ":" + analizar(c.get(Calendar.MINUTE))
                + ":" + analizar(c.get(Calendar.SECOND));
    }

    private BufferedImage rscale(File f) throws IOException {
        BufferedImage image = ImageIO.read(f);
        int height = 300;
        int width = image.getWidth() * 300 / image.getHeight();
        boolean w = false, h = false;
        if (image.getWidth() < width) {
            width = image.getWidth();
            w = true;
        }
        if (image.getHeight() < height) {
            height = image.getHeight();
            h = true;
        }
        if (w && h) {
            return image;
        }
        Image originalImage = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        int type = ((image.getType() == 0) ? BufferedImage.TYPE_INT_ARGB : image.getType());
        BufferedImage resizedImage = new BufferedImage(width, height, type);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(originalImage, 0, 0, width, height, null);
        g2d.dispose();
        g2d.setComposite(AlphaComposite.Src);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return resizedImage;
    }

    private void revisarArchivos(File[] f, File f11) throws IOException {
        for (File f1 : f) {
            String name = f1.getName();
            File file = new File(f11.getPath() + "/" + name);
            if (f1.isDirectory()) {
                if (!file.exists()) {
                    file.mkdirs();
                }
                revisarArchivos(f1.listFiles(), file);
            } else {
                String extension = obtenerExtension(name);
                if (foto(extension) && !file.exists()) {
                    ImageIO.write(rscale(f1), extension, file);
                }
            }
        }
    }

    private String[] buscar(HttpServletRequest request) {
        String[] cmds = {"nombreArchivo", "image", "alterno"};
        for (int i = 0; i < cmds.length; i++) {
            String param = request.getParameter(cmds[i]);
            if (param != null && !param.isEmpty()) {
                return new String[]{param, String.valueOf(i)};
            }
        }
        return new String[]{"", String.valueOf(cmds.length)};
    }

    private void depurar(File[] f, File f11) {
        for (File f1 : f) {
            File file = new File(f11.getAbsolutePath() + "/" + f1.getName());
            if (!file.exists() && !file.getName().equals("Ayuda")) {
                if (f1.isDirectory()) {
                    depurar(f1.listFiles(), file);
                }
                f1.delete();
            } else if (file.isDirectory()) {
                depurar(f1.listFiles(), file);
            }
        }
    }

    private boolean foto(String extension) {
        return (extension.equals("jpg") || extension.equals("png") || extension.equals("jpeg"));
    }

    private String obtenerExtension(String name) {
        int p = name.lastIndexOf(".");
        if (p == -1) {
            return "";
        }
        return name.substring(p + 1, name.length()).toLowerCase();
    }

    private String buscarImagen(String ip, String enlace, String name) {
        String ext = obtenerExtension(name);
        if (foto(ext)) {
            return ip + "?image=" + enlace + name;
        }
        if (new File(ayuda + ext + ".png").exists()) {
            return ip + "?alterno=" + ext + ".png";
        } else {
            BufferedImage img = new BufferedImage(400, 250, BufferedImage.TYPE_4BYTE_ABGR);
            Graphics g = img.getGraphics();
            g.setColor(new Color(150, 150, 150));
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 60));
            g.drawString(ext.toUpperCase(), (400 - g.getFontMetrics().stringWidth(ext.toUpperCase())) / 2, 125 + g.getFontMetrics().getAscent() / 4);
            g.dispose();
            try {
                ImageIO.write(img, "PNG", new File(ayuda + ext + ".png"));
                return ip + "?alterno=" + ext + ".png";
            } catch (IOException ex) {
                Logger.getLogger(ServidorDescargarCargar.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return "";
    }

    private void crearArchivos() {
        File f = new File(carpeta);
        if (!f.exists()) {
            f.mkdirs();
        }
        f = new File(alternativa);
        if (!f.exists()) {
            f.mkdirs();
        }
        f = new File(ayuda);
        if (!f.exists()) {
            f.mkdirs();
        }
    }

}
