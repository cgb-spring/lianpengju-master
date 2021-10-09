<%--
  Created by IntelliJ IDEA.
  User: p'c
  Date: 2021/3/26
  Time: 14:35
  To change this template use File | Settings | File Templates.
--%>

<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Title</title>
</head>
<%
    pageContext.setAttribute("ctx",request.getContextPath());
%>
<body>
<form action="${ctx}/submit" method="post">
    <input type="submit" value="提交">
</form>
</body>
</html>
