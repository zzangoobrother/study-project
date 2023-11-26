### 요구사항

1. index.html 응답하기
'http://localhost:8080/index.html'로 접속했을 때 index.html 파일을 읽어 클라이언트에 응답한다.

2. Get 방식으로 회원가입하기
"회원가입" 메뉴를 클릭하면 "http://localhost:8080/user/form.html"으로 이동, 회원가입을 하면 다음과 같은 형태로 입력값이 서버에 전달된다.
"/user/create?userId=choiseon&password=password&name=ChoiSeon&email=sksk@naver.com"
HTML과 URL을 비교해 사용자가 입력한 값을 model.User 클래스에 저장한다.
3. Post 방식으로 회원가입하기
기존 Get 방식으로 된 회원가입을 Post 방식으로 변경하여 회원가입을 한다.
