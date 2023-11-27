### 요구사항

1. index.html 응답하기
'http://localhost:8080/index.html'로 접속했을 때 index.html 파일을 읽어 클라이언트에 응답한다.

2. Get 방식으로 회원가입하기
"회원가입" 메뉴를 클릭하면 "http://localhost:8080/user/form.html"으로 이동, 회원가입을 하면 다음과 같은 형태로 입력값이 서버에 전달된다.
"/user/create?userId=choiseon&password=password&name=ChoiSeon&email=sksk@naver.com"
HTML과 URL을 비교해 사용자가 입력한 값을 model.User 클래스에 저장한다.
3. Post 방식으로 회원가입하기
기존 Get 방식으로 된 회원가입을 Post 방식으로 변경하여 회원가입을 한다.
4. 302 status code 적용
회원가입 완료 후 index.html 페이지로 이동하지만 url은 /user/create로 유지되는 상태이다. 문제는 기존 회원가입한 데이터들을 기억하고 있어 새로고침하면 
기존 회원가입 데이터들이 또 등록된다. 이를 해결하기 위해 /index.html로 변경해야 한다.
