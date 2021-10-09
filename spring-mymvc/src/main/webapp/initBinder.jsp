<%--
  Created by IntelliJ IDEA.
  User: p'c
  Date: 2021/4/27
  Time: 13:42
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
<form action="${ctx}/testBean" method="post">
    name: <input type="text" name="u.name"> <br>
    age: <input type="text" name="u.age"> <br>
    name: <input type="text" name="s.name"> <br>
    age: <input type="text" name="s.age"> <br>
    <input type="submit">
</form>
</body>
</html>
