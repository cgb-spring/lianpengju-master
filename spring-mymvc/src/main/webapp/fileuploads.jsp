<%--
  Created by IntelliJ IDEA.
  User: p'c
  Date: 2021/4/12
  Time: 15:36
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    pageContext.setAttribute("ctx",request.getContextPath());
%>
<html>
<head>
    <title>Title</title>
</head>
<body>
<h2>上传多个文件 实例</h2>
<form action="${ctx}/filesUpload" method="post"  enctype="multipart/form-data">
    <p>选择文件:<input type="file" name="files"></p>
    <p>选择文件:<input type="file" name="files"></p>
    <p><input type="submit" value="提交"></p>
</form>
</body>
</html>
