# SkiesCrates · fork da Pokémania

Fork do [PokeSkies/SkiesCrates](https://github.com/PokeSkies/SkiesCrates) (MPL-2.0) para a rede Pokémania
(Cobblemon 1.8.1, Fabric 1.21.1). Parte do commit `c610e73` (é a 1.0.4 do Modrinth: publicada 7 minutos depois desse
commit, com o mesmo conserto do changelog dela) e acrescenta, sem mudar nada dos configs antigos:

- **Abertura nova**: roleta (estilo loot box), cartas, abrir várias de uma vez (x10) e pular animação;
- **Raridades** por prêmio (comum → mítico), com cor, som e efeitos;
- **Preview novo** com a chance real em % de cada prêmio, ordenado por raridade, com filtro e páginas;
- **Anúncio na rede** de todos os prêmios (mais destaque nos raros), pelo Synx Connect;
- **Efeitos no bloco** (o prêmio sobe num display entity, partículas, fogos, totem);
- **ManiaUI** (opcional): telas com os Pokémon em 3D e o app "Caixas" no Pokélógio. Sem o cliente, baú;
- **Entrega garantida**: o prêmio é sorteado e gravado num diário antes da animação.

Jar: `SkiesCrates-fabric-1.21.1-1.1.0-mania.jar`. O código novo fica em `com.pokeskies.skiescrates.mania`; os arquivos
do SkiesCrates que mudaram têm o aviso da MPL no topo ("Modificado pela Pokémania"). Histórico: [CHANGELOG.md](CHANGELOG.md).

## Regras (não negociáveis)

1. **O prêmio é sorteado antes da animação**, pelos `weight` do config, e entra no diário (`pending` dos dados do
   jogador) **na mesma gravação** que tira as chaves. A animação só mostra o resultado.
2. A faixa da roleta, as casas do x10 e as cartas usam prêmios sorteados com **os mesmos weights reais**. Nenhum
   "quase ganhou" forjado: só a casa onde a roleta para é o prêmio; as outras são sorteios honestos.
3. **Toda caixa mostra a chance real** de cada prêmio (preview, tooltip, resultado). Calculada dos weights, só com os
   prêmios que o jogador ainda pode receber (limites).
4. **O servidor é a fonte da verdade.** Entrega: no fim da animação, ao fechar o menu, ao sair do servidor (fase
   antecipada do evento, antes de os outros mods salvarem o jogador) ou ao desligar — o que vier primeiro, uma vez só.
   Se o servidor cair no meio, o prêmio é entregue quando o jogador entrar de novo (5 s depois).
5. Toda leitura-alteração-gravação dos dados do jogador passa pela mesma fila por jogador dos comandos de chave e relê
   do banco antes de gravar (antes, uma chave dada no meio da animação podia ser apagada).
6. Textos curtos (pedido do dono): item, nome curto, cor da raridade e a chance; detalhe só no tooltip, numa linha.

## Campos novos (todos opcionais)

### Na caixa (`crates/<id>.json`)

| Campo | Padrão | O que faz |
|---|---|---|
| `"opening"` | `abertura.padrao` do mania.json | `"roleta"`, `"cartas"` ou `"classica"` (volta para o `animation` antigo) |
| `"rarity"` | — | Raridade dos prêmios sem `rarity` (ex: `"epico"` numa caixa em que todos os weights são iguais) |
| `"multi_open"` | `abertura.multiplas` (10) | Quantas abrir de uma vez no "Abrir xN" (0 desliga, máx. 21) |
| `"keys_info"` | `preview.ondeConseguir` | Linhas do botão "Onde conseguir chave" |
| `"keys_link"` | `preview.link` | Link aberto no chat por esse botão |
| `"announce"` | `true` | `false` = sem anúncio na rede nesta caixa (e os comandos `announce` do config voltam a rodar) |
| `"new_preview"` | `preview.todas` | `false` = volta ao preview antigo (`preview`) |

### No prêmio (`rewards.<id>`)

| Campo | O que faz |
|---|---|
| `"rarity"` | `comum`, `incomum`, `raro`, `epico`, `lendario`, `mitico` (aceita acento e o nome em inglês) |
| `"description"` | Linha curta no lugar do resumo automático ("✦ Shiny · 6 IVs 31", "×32", "+1 chave") |

Sem `rarity`, a raridade sai do **weight do prêmio ÷ maior weight da caixa** (`mania.json` → `deducao`):
≥ 0,75 comum · ≥ 0,45 incomum · ≥ 0,20 raro · ≥ 0,08 épico · ≥ 0,03 lendário · abaixo, mítico. Numa caixa com todos
os weights iguais, todos ficam com a mesma raridade (a chance é a mesma; use o `rarity` da caixa para dar o tom).

Exemplo (Caixa Shiny no deploy):

```json
"animation": "storm",
"opening": "roleta",
"multi_open": 10,
"keys_info": ["<gray>Chave Shiny: <white>loja do site</white>, <white>kits VIP</white> e eventos."],
```

### `config/skiescrates/mania.json`

Criado sozinho com os padrões na primeira vez (fica fora do `config.json`, que tem os dados do banco). Seções:

- `ativo`: `false` desliga tudo da Pokémania (as caixas funcionam exatamente como no 1.0.4).
- `preview`: `todas` (preview novo em todas as caixas), `ondeConseguir`, `link`, `distanciaAbrir` (8 blocos),
  `ondeFica` (texto para quem abriu o preview longe da caixa).
- `abertura`: `padrao` (`roleta`), `multiplas` (10), `pularComShift`, `roleta` (passos, pausa máxima, curva, sons,
  marcador, vidro), `cartas` (verso, subida, som), `multipla` (início, intervalo).
- `raridades`: nome, cor, vidro, som, partícula e som do anúncio de cada raridade.
- `deducao`: os limites acima.
- `anuncio`: `ativo`, `minimo` (comum = todas), `rede` (Synx Connect), `pularComandos` (`announce `, `broadcast `:
  os configs antigos anunciam em todo prêmio por comando; com o anúncio novo esses comandos são pulados),
  `resumoMaximo`.
- `mundo`: item subindo no bloco, partículas, fogos/totem/título a partir de qual raridade.
- `maniaui`: `ativo`, `app` (app "Caixas" no Pokélógio), `cor`.
- `textos`: todos os textos (MiniMessage), inclusive os modelos do anúncio por raridade.

## Como o jogador usa

- **Clique esquerdo** no bloco: preview. **Clique direito** (com chave): abre com a animação da caixa.
  **Shift + clique direito**: abre sem animação.
- No preview: **Abrir**, **Abrir xN** (até N, limitado pelas chaves), **Onde conseguir chave**, filtro por raridade.
  Longe da caixa (mais de 8 blocos), os botões de abrir viram "Vá até a caixa".
- Durante a animação: **shift + clique** (ou o botão) pula. Nas cartas, cada clique vira uma carta (a mais rara por
  último); "Revelar todas" vira o resto.
- `/caixas` (`/caixa`): todas as caixas com as suas chaves. `/caixas preview <caixa>`.
  `/caixas animacao rapida|completa`: sempre pular a animação (fica salvo).

## Comandos de admin (`skiescrates.command.caixas.admin`, nível 2)

- `/caixas admin simular <caixa> <vezes>`: sorteia sem entregar e compara com a chance calculada, por raridade.
- `/caixas admin pendentes <jogador>`: o diário do jogador.
- `/caixas admin abrir <jogador> <caixa> [vezes]`: abre de graça (teste e suporte).

## Anúncio na rede

Canal `skiescrates/anuncio` do Synx Connect (o mesmo do Gladiador e da Ranqueada; sem Carbon). Todo prêmio é
anunciado; a linha cresce com a raridade (épico em destaque com som, lendário e mítico com banner). Abrir várias vira
uma linha de resumo ("1 Épico, 9 Comum") com hover de tudo, mais uma linha própria para cada lendário/mítico. O nome do
prêmio tem hover com o item de verdade. Só servidores com o SkiesCrates ouvem o canal (hubs e raids); para os realms
receberem, falta um receptor no Pokélógio (classe `NetworkAnnounce`: `origin`, `lines` em JSON, `sound`).

## ManiaUI

Soft-dependency (`suggests`), com a ponte `mania/ui/UiHook` sem imports do ManiaUI e guarda `LinkageError`, no padrão do
`GladiadorApp`/`RankedApp`. Compilado contra o `libs/ManiaUI-fabric-1.21.1-0.4.2.jar` (só API pública; nenhuma
assinatura do ManiaUI mudou). O `itemModel` (0.4.2) fica isolado em `ItemModelCompat`: com ManiaUI mais
antigo no servidor ou no cliente, os itens caem no tile de grade. Menus: `skiescrates_preview`, `skiescrates_abertura` (remonta a cada 2 ticks durante a
animação), `skiescrates_caixas`; app `caixas` (ordem 40).

## Armazenamento

`UserData` ganhou `pending` (lista de `PendingRewardData`: id, caixa, prêmio, hora) e `skipAnimation`. Mongo e JSON
gravam os campos novos sozinhos (documentos antigos vêm sem). **SQL/SQLite não gravam o diário** (as colunas são só
`crates` e `keys`): com SQL, uma queda no meio da animação perde aquele prêmio, como no 1.0.4.

## Compilar e testar

- JDK 21: `export JAVA_HOME=~/.jdks/jdk-21.0.12.1+1 && ./gradlew build` → `build/libs/SkiesCrates-fabric-1.21.1-1.1.0-mania.jar`.
- Servidor de teste próprio em `teste/` (fora do git): `teste/servidor.sh` (porta 25677, Redis falso na 6377, storage
  JSON, jar do servidor renomeado para `caixas-server-launch.jar` para os scripts de outros projetos não o matarem),
  `node teste/fake-redis.js 6377`, `teste/cliente.sh` (ManiaUI 0.4.1 clonado em `teste/maniaui-cliente`, jogador
  "Caixeiro"; `DEBUG_EVERY=N` tira print a cada N ticks com uma tela aberta). `teste/queda.sh`: kill -9 no meio da
  animação. Com o Connect e sem outros mods, o servidor de teste não fecha sozinho depois do `stop` (threads do
  Redisson do Connect, acontece também sem este mod): mate o processo.

---

## SkiesCrates (original)

- Future Updates
  - Prevent animation out of bounds infinite loops
  - Require keys in hand option
  - Logout and server shutdown handling
  - More Placeholders
  - Logging
    - Opening
    - Unique key duplication
  - More sound options?
  - In-game Editor
  - Storage migration settings (json -> sql, etc.)
  - Broadcast message definition in-line with the rewards
    - "broadcast": true
    - "broadcast": [ "message" ]
