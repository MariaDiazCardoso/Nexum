package com.nexum.service;
import com.nexum.entity.Certificado;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import java.awt.Color;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
@Service
public class CertificadoPdfService {
 private final String frontend;
 public CertificadoPdfService(@Value("${app.frontend-url:http://127.0.0.1:4306}") String frontend){this.frontend=frontend.replaceAll("/+$","");}
 private static final Color NAVY=new Color(23,37,84),BLUE=new Color(37,99,235),MUTED=new Color(83,101,128),VIOLET=new Color(124,58,237);
 private static String clean(String s){return Objects.toString(s,"").replaceAll("[\\p{Cntrl}]"," ").trim();}
 private String text(PDFont font,String value) throws IOException {
  var out=new StringBuilder();
  for(int cp:clean(value).codePoints().toArray()){
   String c=new String(Character.toChars(cp));try{font.encode(c);out.append(c);}catch(IllegalArgumentException e){out.append('?');}
  }return out.toString();
 }
 private void line(PDPageContentStream cs,PDFont f,float size,float x,float y,String text,Color color) throws IOException {
  cs.setNonStrokingColor(color);cs.beginText();cs.setFont(f,size);cs.newLineAtOffset(x,y);cs.showText(text(f,text));cs.endText();
 }
 private float fit(PDFont f,String text,float size,float width) throws IOException {
  float w=f.getStringWidth(text(f,text))/1000*size;return w>width?size*width/w:size;
 }
 private void center(PDPageContentStream cs,PDFont f,float size,float y,String value,Color color,float pageWidth) throws IOException {
  String t=text(f,value);float actual=fit(f,t,size,pageWidth-170);line(cs,f,actual,(pageWidth-f.getStringWidth(t)/1000*actual)/2,y,t,color);
 }
 private List<String> wrap(PDFont font,String value,float size,float maxWidth) throws IOException {
  var result=new ArrayList<String>();String current="";
  for(int cp:clean(value).codePoints().toArray()){
   String c=new String(Character.toChars(cp));String candidate=current+c;
   if(font.getStringWidth(text(font,candidate))/1000*size>maxWidth && !current.isEmpty()){
    int space=current.lastIndexOf(' ');
    if(space>0){result.add(current.substring(0,space).trim());current=current.substring(space+1)+c;}
    else{result.add(current);current=c;}
   }else current=candidate;
  }
  if(!current.isBlank())result.add(current.trim());return result;
 }
 public byte[] generar(Certificado c) throws IOException {
  try(var doc=new PDDocument();var normalInput=new ClassPathResource("fonts/Vera.ttf").getInputStream();var boldInput=new ClassPathResource("fonts/VeraBd.ttf").getInputStream();var output=new ByteArrayOutputStream()) {
   var normal=PDType0Font.load(doc,normalInput);var bold=PDType0Font.load(doc,boldInput);
   float w=842,h=595;var page=new PDPage(new PDRectangle(w,h));doc.addPage(page);
   doc.getDocumentInformation().setTitle("Certificado de aprendizaje | NEXUM");doc.getDocumentInformation().setAuthor("NEXUM");
   try(var cs=new PDPageContentStream(doc,page)){
    cs.setNonStrokingColor(Color.WHITE);cs.addRect(0,0,w,h);cs.fill();
    cs.setNonStrokingColor(NAVY);cs.addRect(0,0,20,h);cs.fill();
    cs.setStrokingColor(new Color(220,228,239));cs.setLineWidth(1);cs.addRect(43,34,w-77,h-68);cs.stroke();
    for(int n=0;n<5;n++){cs.setNonStrokingColor(n<3?BLUE:VIOLET);cs.addRect(72+n*9,508+n*6,7,7);cs.fill();}
    line(cs,bold,21,130,527,"NEXUM",NAVY);
    line(cs,normal,9,590,532,"APRENDE. AVANZA. TRANSFORMA.",MUTED);
    center(cs,normal,10,461,"UN LOGRO EN TU CAMINO DE APRENDIZAJE",BLUE,w);
    center(cs,bold,34,410,"Certificado de finalización",NAVY,w);
    center(cs,normal,12,373,"Se otorga el presente certificado a",MUTED,w);
    float nameY=335,nameSize=28;
    var names=wrap(bold,c.getNombreAlumno(),nameSize,650);
    while(nameSize>10 && names.size()*(nameSize+5)>82){nameSize--;names=wrap(bold,c.getNombreAlumno(),nameSize,650);}
    for(int n=0;n<names.size();n++)center(cs,bold,nameSize,nameY-n*(nameSize+5),names.get(n),NAVY,w);
    float courseIntro=Math.min(281,nameY-names.size()*(nameSize+5)-10);
    center(cs,normal,12,courseIntro,"por completar satisfactoriamente el curso",MUTED,w);
    float courseY=courseIntro-32,courseSize=22;
    var courses=wrap(bold,c.getTituloCurso(),courseSize,650);
    while(courseSize>10 && courseY-(courses.size()-1)*(courseSize+5)<179){courseSize--;courses=wrap(bold,c.getTituloCurso(),courseSize,650);}
    for(int n=0;n<courses.size();n++)center(cs,bold,courseSize,courseY-n*(courseSize+5),courses.get(n),BLUE,w);
    cs.setStrokingColor(new Color(220,228,239));cs.moveTo(85,158);cs.lineTo(w-65,158);cs.stroke();
    line(cs,normal,9,85,133,"FECHA DE EMISIÓN",MUTED);
    line(cs,bold,12,85,112,c.getFechaEmision().format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy",Locale.forLanguageTag("es-PE"))),NAVY);
    line(cs,normal,9,440,133,"CÓDIGO DE VERIFICACIÓN",MUTED);
    line(cs,bold,10,440,113,c.getCodigoVerificacion(),NAVY);
    line(cs,normal,8,85,71,"NEXUM | Plataforma de aprendizaje",MUTED);
    line(cs,normal,8,440,71,"Verificar autenticidad en NEXUM",BLUE);
    var link=new PDAnnotationLink();var border=new org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary();border.setWidth(0);link.setBorderStyle(border);link.setRectangle(new PDRectangle(437,64,290,23));
    var action=new PDActionURI();action.setURI(frontend+"/verify-certificate?codigo="+c.getCodigoVerificacion());link.setAction(action);page.getAnnotations().add(link);
   }
   doc.save(output);return output.toByteArray();
  }
 }
}
