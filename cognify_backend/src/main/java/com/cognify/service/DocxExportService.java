package com.cognify.service;

import com.cognify.repository.*;
import java.io.*;
import java.math.BigInteger;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DocxExportService {
  private final DocumentRepository documents;
  private final DocumentSectionRepository sections;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public byte[] export(UUID owner, UUID id) {
    var source =
        documents
            .findByIdAndOwnerId(id, owner)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    var parts = sections.findByDocumentIdOrderByPositionAsc(id);
    try (XWPFDocument doc = new XWPFDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      var sect = doc.getDocument().getBody().addNewSectPr();
      var size = sect.addNewPgSz();
      size.setW(BigInteger.valueOf(11906));
      size.setH(BigInteger.valueOf(16838));
      var margin = sect.addNewPgMar();
      margin.setTop(BigInteger.valueOf(1134));
      margin.setBottom(BigInteger.valueOf(1134));
      margin.setLeft(BigInteger.valueOf(1701));
      margin.setRight(BigInteger.valueOf(850));
      sect.addNewTitlePg();
      XWPFStyles styles = doc.createStyles();
      CTStyle heading = CTStyle.Factory.newInstance();
      heading.setStyleId("Heading1");
      heading.setType(STStyleType.PARAGRAPH);
      heading.addNewName().setVal("heading 1");
      heading.addNewPPr().addNewOutlineLvl().setVal(BigInteger.ZERO);
      styles.addStyle(new XWPFStyle(heading));
      var title = doc.createParagraph();
      title.setAlignment(ParagraphAlignment.CENTER);
      title.setSpacingBefore(1200);
      var run = title.createRun();
      run.setFontFamily("Sylfaen");
      run.setFontSize(22);
      run.setBold(true);
      run.setText(clean(source.getTitle()));
      if ("MOCK".equals(source.getGenerationMode())) {
        var p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        var r = p.createRun();
        r.setColor("B00020");
        r.setFontSize(14);
        r.setText("ԹԵՍՏԱՅԻՆ ՓԱՍՏԱԹՈՒՂԹ — ոչ իրական AI արդյունք");
      }
      doc.createParagraph().createRun().addBreak(BreakType.PAGE);
      if (!parts.isEmpty()) {
        var p = doc.createParagraph();
        var r = p.createRun();
        r.setFontFamily("Sylfaen");
        r.setFontSize(18);
        r.setBold(true);
        r.setText("Բովանդակություն");
        for (var s : parts) paragraph(doc, s.getTitle());
        doc.createParagraph().createRun().addBreak(BreakType.PAGE);
        for (var s : parts) {
          var h = doc.createParagraph();
          h.setStyle("Heading1");
          h.setKeepNext(true);
          var hr = h.createRun();
          hr.setBold(true);
          hr.setFontSize(16);
          hr.setFontFamily("Sylfaen");
          hr.setText(clean(s.getTitle()));
          for (String line : s.getContent().split("\\R", -1)) paragraph(doc, line);
        }
      } else for (String line : source.getContent().split("\\R", -1)) paragraph(doc, line);
      doc.createFooter(HeaderFooterType.FIRST).createParagraph();
      var footer = doc.createFooter(HeaderFooterType.DEFAULT).createParagraph();
      footer.setAlignment(ParagraphAlignment.CENTER);
      footer.getCTP().addNewFldSimple().setInstr("PAGE");
      doc.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("DOCX export failed", e);
    }
  }

  private void paragraph(XWPFDocument doc, String text) {
    var p = doc.createParagraph();
    p.setAlignment(ParagraphAlignment.BOTH);
    p.setSpacingBetween(1.5);
    p.setFirstLineIndent(567);
    p.setSpacingAfter(120);
    var r = p.createRun();
    r.setFontFamily("Sylfaen");
    r.setFontSize(14);
    r.setText(clean(text));
  }

  private String clean(String text) {
    return text.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "");
  }
}
