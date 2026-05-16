package io.searchable.admin.controller;

import io.searchable.core.application.NamespaceService;
import io.searchable.core.domain.namespace.Namespace;
import io.searchable.admin.service.FileUploadService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/documents")
public class DocumentUploadController {

    private final NamespaceService namespaceService;
    private final FileUploadService uploader;

    public DocumentUploadController(final NamespaceService namespaceService,
                                    final FileUploadService uploader) {
        this.namespaceService = namespaceService;
        this.uploader = uploader;
    }

    @GetMapping("/upload")
    public String form(final Model model) {
        final List<Namespace> namespaces = namespaceService.listAll();
        model.addAttribute("activeNav", "documents");
        model.addAttribute("pageTitle", "Upload Document");
        model.addAttribute("namespaces", namespaces);
        return "documents/upload";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("namespaceId") final String namespaceId,
                         @RequestParam("file") final MultipartFile file,
                         final RedirectAttributes flash) {
        final FileUploadService.UploadResult result = uploader.upload(namespaceId, file);
        flash.addFlashAttribute("flashSuccess",
            "'" + result.fileName() + "' をインデックスしました (parser=" + result.parserName()
                + ", id=" + result.documentId() + ")");
        return "redirect:/indexes/" + namespaceId;
    }
}
