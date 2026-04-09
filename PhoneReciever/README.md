# PhoneReciever

## Descricao

`PhoneReciever` e um aplicativo Android nativo em Kotlin criado para receber mensagens enviadas por um dispositivo Wear OS usando a API de Data Layer do Google Play Services, mais especificamente `MessageClient`.

No estado atual, o projeto funciona como um receptor simples: abre uma tela unica no celular, fica aguardando mensagens do relogio e, quando recebe dados no caminho `"/ping"`, atualiza o texto exibido na interface.

O projeto parece estar em fase inicial de prototipo ou prova de conceito. A base esta pequena, facil de entender e boa para experimentacao, mas ainda contem alguns vestigios do template do Android Studio que valem ser ajustados se a aplicacao continuar evoluindo.

---

## Objetivo do projeto

O objetivo atual do app e servir como lado Android phone/mobile de uma comunicacao com Wear OS, recebendo mensagens simples enviadas por outro dispositivo conectado.

Casos de uso que esse projeto sugere:

- validar a conexao entre celular e relogio
- testar troca de mensagens via Data Layer API
- servir de base para comandos disparados do Wear OS
- exibir rapidamente no celular o conteudo enviado pelo relogio

---

## Funcionalidades atuais

- inicializa uma `MainActivity`
- cria uma interface simples usando um `TextView`
- exibe um estado inicial de espera por mensagens
- registra um listener do `Wearable.getMessageClient(this)` quando a tela entra em foco
- remove o listener quando a activity sai de foco
- recebe mensagens `MessageEvent`
- filtra mensagens pelo path `"/ping"`
- converte os bytes recebidos para `String`
- atualiza o texto na tela com o conteudo recebido

---

## Como o app funciona

### Fluxo resumido

1. O usuario abre o aplicativo no celular.
2. A `MainActivity` cria um `TextView` programaticamente e define a mensagem inicial.
3. Em `onResume()`, o app registra um listener no `MessageClient`.
4. Quando o relogio envia uma mensagem no path `"/ping"`, o metodo `onMessageReceived()` e disparado.
5. O app converte o payload recebido em texto.
6. A interface e atualizada na thread principal mostrando a mensagem recebida.
7. Em `onPause()`, o listener e removido para evitar registros desnecessarios enquanto a tela nao estiver ativa.

### Caminho de mensagem esperado

O codigo atual so processa mensagens recebidas com:

```text
/ping
```

Qualquer outro path sera ignorado.

### Exemplo de comportamento esperado

Se o relogio enviar os bytes correspondentes ao texto:

```text
Ola do relogio
```

o app deve passar a exibir algo como:

```text
Recebido do relogio:
Ola do relogio
```

---

## Stack e tecnologias

- Kotlin
- Android SDK
- Gradle com Kotlin DSL
- Google Play Services Wearable (`play-services-wearable`)
- AndroidX
- Jetpack Compose configurado no projeto
- Material 3 configurado no projeto

### Versoes observadas no projeto

- Android Gradle Plugin: `9.1.0`
- Kotlin: `2.2.10`
- `minSdk`: `24`
- `targetSdk`: `36`
- `compileSdk`: `36` com `minorApiLevel = 1`
- Wearable API: `com.google.android.gms:play-services-wearable:19.0.0`

Observacao importante:

- Embora o projeto tenha Compose habilitado e arquivos de tema Material 3, a tela atual nao usa Compose; ela usa `TextView` diretamente dentro da `MainActivity`.

---

## Estrutura do projeto

```text
PhoneReciever/
|- app/
|  |- src/
|  |  |- main/
|  |  |  |- java/com/example/phonereciever/
|  |  |  |  |- MainActivity.kt
|  |  |  |  |- ui/theme/
|  |  |  |- AndroidManifest.xml
|  |  |  |- res/
|  |  |- test/
|  |  |- androidTest/
|  |- build.gradle.kts
|- gradle/
|  |- libs.versions.toml
|- build.gradle.kts
|- settings.gradle.kts
|- gradle.properties
```

### Arquivos principais

#### `app/src/main/java/com/example/phonereciever/MainActivity.kt`

Arquivo central da aplicacao no estado atual.

Responsabilidades:

- criar a interface simples com `TextView`
- registrar e remover o listener de mensagens
- receber os eventos do `MessageClient`
- filtrar o path `"/ping"`
- atualizar a UI com o texto recebido

#### `app/src/main/AndroidManifest.xml`

Define:

- a aplicacao Android
- a activity principal
- o launcher entry point do app

#### `app/build.gradle.kts`

Contem:

- configuracao do modulo Android
- `namespace`
- `applicationId`
- versoes min/target SDK
- dependencias do app
- habilitacao de Compose

#### `gradle/libs.versions.toml`

Centraliza as versoes e aliases das dependencias do projeto.

#### `app/src/main/java/com/example/phonereciever/ui/theme/*`

Arquivos de tema gerados pelo template Compose:

- `Color.kt`
- `Theme.kt`
- `Type.kt`

Hoje esses arquivos estao presentes, mas nao participam da tela principal atual.

---

## Detalhamento tecnico da implementacao atual

### Activity principal

A `MainActivity`:

- herda de `ComponentActivity`
- implementa `MessageClient.OnMessageReceivedListener`

Mesmo herdando de uma activity com suporte comum a Compose, o app atualmente nao usa `setContent { ... }`. Em vez disso, cria um `TextView` manualmente e o define como conteudo da tela com `setContentView(textView)`.

### Ciclo de vida

O listener de mensagens e controlado pelo ciclo de vida da activity:

- `onResume()` registra o listener
- `onPause()` remove o listener

Esse comportamento e adequado para um prototipo simples porque evita manter o listener ativo quando a activity nao esta em primeiro plano.

### Recepcao da mensagem

Ao receber um `MessageEvent`, o app:

1. verifica se `messageEvent.path == "/ping"`
2. converte `messageEvent.data` para `String`
3. usa `runOnUiThread` para atualizar o `TextView`

### Interface atual

A interface e minima e totalmente programatica:

- sem XML de layout para a tela principal
- sem Compose renderizando conteudo
- sem ViewBinding
- sem estados visuais adicionais

Isso deixa o projeto simples para entendimento inicial, mas tambem limita escalabilidade caso a tela fique mais complexa.

---

## Requisitos para executar

- Android Studio instalado
- Android SDK configurado
- JDK compativel com o ambiente do Android Studio
- um dispositivo Android fisico ou emulador para rodar o app mobile
- opcionalmente, um app Wear OS ou outro emissor capaz de enviar mensagens para o celular pelo path `"/ping"`

---

## Como executar o projeto

### Pelo Android Studio

1. Abra a pasta do projeto no Android Studio.
2. Aguarde a sincronizacao do Gradle.
3. Selecione um dispositivo Android ou emulador.
4. Execute o modulo `app`.

### Pela linha de comando

No diretorio raiz do projeto:

```powershell
.\gradlew.bat assembleDebug
```

Para instalar em um dispositivo conectado:

```powershell
.\gradlew.bat installDebug
```

Observacao:

- eu nao executei esses comandos nesta etapa; as instrucoes acima foram montadas a partir da estrutura padrao do projeto Android/Gradle presente no repositorio.

---

## Como testar manualmente

Para validar o comportamento atual:

1. Execute o app no celular.
2. Mantenha a tela principal aberta.
3. Envie uma mensagem do dispositivo Wear OS para o celular usando o path `"/ping"`.
4. Confirme que o texto exibido no app muda de "Aguardando mensagem do relogio..." para "Recebido do relogio:" seguido do conteudo recebido.

### O que precisa existir no emissor

O dispositivo emissor deve:

- estar pareado/conectado no ecossistema Wear OS/Google Play Services
- usar `MessageClient` ou API equivalente
- enviar a mensagem para o path `"/ping"`
- enviar um payload que possa ser interpretado como texto

---

## Testes presentes no projeto

O projeto contem os testes padrao criados pelo template:

- `app/src/test/java/com/example/phonereciever/ExampleUnitTest.kt`
- `app/src/androidTest/java/com/example/phonereciever/ExampleInstrumentedTest.kt`

### Situacao atual dos testes

- o teste unitario e apenas um exemplo trivial de soma
- o teste instrumentado valida o nome do pacote da aplicacao

### Ponto de atencao

Existe uma inconsistencia importante:

- o teste instrumentado espera o pacote `com.example.phonereciever`
- o `applicationId` atual em `app/build.gradle.kts` esta como `com.example.datalayertest`

Isso sugere que o teste instrumentado provavelmente falhara se executado sem ajustes.

---

## Inconsistencias e pontos de atencao

Durante a analise, apareceram alguns pontos que valem ser documentados para manutencao futura.

### 1. Divergencia entre pacote, namespace e estrutura de pastas

Atualmente existe mistura entre:

- `com.example.phonereciever`
- `com.example.datalayertest`

Impactos possiveis:

- confusao na manutencao
- testes quebrando
- referencias inconsistentes no projeto
- dificuldade para evoluir o app com mais modulos e telas

### 2. Nome do projeto

O nome atual e:

```text
PhoneReciever
```

O termo correto em ingles costuma ser:

```text
PhoneReceiver
```

Isso nao impede o funcionamento, mas pode valer ajuste para padronizacao e legibilidade.

### 3. Compose configurado, mas nao utilizado

O projeto possui:

- dependencias Compose
- tema Material 3
- `buildFeatures { compose = true }`

Mas a interface atual usa `TextView` tradicional. Isso nao e errado, mas indica que o projeto provavelmente foi criado com template Compose e depois simplificado manualmente.

### 4. Encoding de texto

Na leitura do codigo, a string com "relogio" aparece com caracteres corrompidos em alguns contextos, algo como "relÃ³gio". Isso costuma indicar problema de encoding em algum momento da edicao ou exibicao.

Vale revisar os arquivos fonte para manter acentuacao consistente em UTF-8.

### 5. Estrutura ainda muito acoplada

Toda a logica principal esta dentro da `MainActivity`.

Para um projeto pequeno isso e aceitavel, mas se o app crescer pode ser interessante separar:

- camada de comunicacao com Wear OS
- camada de estado/UI
- parsing/validacao das mensagens

---

## Melhorias recomendadas

### Curto prazo

- alinhar `package`, `namespace` e `applicationId`
- corrigir o nome do projeto caso deseje padronizacao em ingles
- revisar encoding dos textos exibidos
- remover dependencias e arquivos nao usados ou migrar a interface para Compose de vez
- ajustar o teste instrumentado para refletir o pacote real da aplicacao

### Medio prazo

- extrair a logica de mensagens para uma classe ou camada dedicada
- criar uma representacao de estado da tela
- adicionar logs estruturados para depuracao da comunicacao com o relogio
- tratar paths diferentes alem de `"/ping"`
- validar payloads invalidos ou vazios

### Longo prazo

- adotar arquitetura como MVVM se o app crescer
- criar interface mais amigavel para historico de mensagens
- integrar reconexao, estado de pareamento e diagnostico
- adicionar testes reais para o fluxo de recebimento de mensagens

---

## Possivel evolucao da arquitetura

Se o projeto deixar de ser apenas um prototipo, uma evolucao natural seria:

- `MainActivity` responsavel apenas pela UI
- uma camada `WearMessageReceiver` para encapsular `MessageClient`
- um `ViewModel` para expor estado observavel
- uma UI Compose ou XML mais estruturada para mostrar status, ultima mensagem, erros e historico

---

## Dependencias principais

Dependencias mais relevantes observadas:

- `com.google.android.gms:play-services-wearable:19.0.0`
- `androidx.core:core-ktx`
- `androidx.lifecycle:lifecycle-runtime-ktx`
- `androidx.activity:activity-compose`
- Compose BOM
- `androidx.compose.material3:material3`

Mesmo que nem todas estejam em uso real na tela atual, elas fazem parte da configuracao do projeto hoje.

---

## Resumo executivo

`PhoneReciever` e hoje um app Android simples para receber mensagens de um relogio Wear OS e exibi-las na tela do celular. A implementacao atual e pequena, direta e facil de acompanhar, o que e bom para testes iniciais.

Ao mesmo tempo, a base ainda carrega inconsistencias de template e nomenclatura, especialmente em relacao a pacotes, identificadores e uso parcial de Compose. Antes de escalar o projeto, vale fazer uma pequena rodada de organizacao tecnica para padronizar a estrutura e reduzir riscos de manutencao.

---

## Referencias internas

Arquivos mais relevantes para consulta:

- `app/src/main/java/com/example/phonereciever/MainActivity.kt`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/test/java/com/example/phonereciever/ExampleUnitTest.kt`
- `app/src/androidTest/java/com/example/phonereciever/ExampleInstrumentedTest.kt`
