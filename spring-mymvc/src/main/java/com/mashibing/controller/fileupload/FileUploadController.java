package com.mashibing.controller.fileupload;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;

@Controller
public class FileUploadController {

    @RequestMapping("fileupload")
    public String upload(MultipartFile file, HttpServletRequest request,String desc) throws IOException {
        System.out.println(desc);
        if (file.isEmpty()){
            return "false";
        }
        String path = request.getServletContext().getRealPath("/WEB-INF/file");
        String fileName = file.getOriginalFilename();
        File filePath = new File(path,fileName);
        if (!filePath.getParentFile().exists()){
            filePath.getParentFile().mkdir();
        }
        file.transferTo(filePath);
        return "success";
    }
}
