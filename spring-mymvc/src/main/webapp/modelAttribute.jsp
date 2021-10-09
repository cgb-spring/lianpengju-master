<%--
  Created by IntelliJ IDEA.
  User: root
  Date: 2020/3/11
  Time: 13:45
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>$Title$</title>
</head>
<body>
<form action="update" method="post">
    <input type="hidden" value="1" name="id">
    姓名：张三<br>
    密码：<input type="text" name="password"><br>
    年龄：<input type="text" name="age"><br>
    <input type="submit" value="提交">
</form>
</body>
</html>