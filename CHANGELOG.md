# Changelog (fork da Pokémania)

## 1.2.2-mania (2026-10-17)

- `mania.json` ganha `"blocos"` (padrão `true`). Com `false`, nenhuma caixa carrega bloco no mundo: o servidor só
  entrega e guarda chaves. Serve para os realms, onde as missões dão chave (`key give`) mas as coordenadas das caixas
  são do mapa do hub.

## 1.2.1-mania (2026-10-13)

- Compila contra a **Cobblemon 1.8.2** (`com.cobblemon:fabric:1.8.2+1.21.1`; antes `1.7.1+1.21.1-SNAPSHOT`). Sem mudança de comportamento.

## 1.2.0-mania (2026-10-12) · vitrine de loot box

- **Preview da caixa** e **lista de caixas** na vitrine em tela cheia do ManiaUI 0.5.0 (`collection`), no estilo do
  Armário: prêmios em 3D (Pokémon do Cobblemon ou o item girando) em cartões com a cor da raridade e a chance real no
  selo; o prêmio escolhido grande à esquerda (arraste para girar, rolagem para zoom); filtros por raridade com a chance
  somada; busca; Abrir (sozinho, grande), Abrir xN e Onde conseguir chave embaixo da prévia. Na lista, cada caixa em 3D
  com as chaves no selo e "Ver prêmios". Esgotado aparece como indisponível. Pouco texto: o detalhe fica na dica.
- Precisa do **ManiaUI 0.5.0** no servidor; sem o cliente 0.5.0 o ManiaUI entrega a grade comum (ou o baú) com os
  mesmos botões. A abertura (roleta, cartas, x10) continua como na 1.1.0.

## 1.1.0-mania (2026-10-10)

Base: SkiesCrates 1.0.4 (commit `c610e73`). Todo config antigo continua funcionando sem mudança.

**Abertura**
- Roleta estilo loot box (baú e ManiaUI): faixa que desacelera, tick com tom subindo, marcador no centro, moldura na
  cor da raridade no fim, som/partículas por raridade, título na tela, fogos (épico+) e totem (lendário+).
- Cartas: uma carta virada por prêmio; cada clique vira uma, da menor raridade para a maior; a moldura "sobe" de
  raridade até a do prêmio.
- Abrir várias (x10, configurável): casas param em sequência e um resumo agrupado por raridade.
- Pular: shift + clique direito no bloco, shift + clique no menu ou `/caixas animacao rapida`.
- Efeitos no bloco: o prêmio sobe num item display (apagado ao carregar, se o servidor cair com ele no ar).

**Preview**
- Ordenado por raridade (moldura/etiqueta na cor), filtro por raridade, páginas, chance real em % e "1 em N",
  resumo curto do que vem (shiny, IVs, quantidade, chave), suas chaves, Abrir / Abrir xN / Onde conseguir chave.
- `/caixas`, `/caixas preview <caixa>`; nomes curtos (sem repetir o nome da caixa e sem tags soltas do config).

**Raridades**: campo `rarity` no prêmio e na caixa; sem ele, deduzida pelo weight relativo.

**Anúncio**: todos os prêmios, com mais destaque nos raros, para a rede pelo Synx Connect (`skiescrates/anuncio`);
os comandos `announce`/`broadcast` dos prêmios são pulados na abertura nova (não duplica).

**ManiaUI**: preview, abertura e lista com Pokémon em 3D; app "Caixas" no Pokélógio. Prêmios que são item aparecem
no mesmo palco do Pokémon (`itemModel` do ManiaUI 0.4.2, parado e de frente), com a mesma altura; cliente até 0.4.1 vê
o tile de grade, também com altura fixa. Item que não existe no servidor (mod faltando) vira papel em vez de casa vazia.

**Segurança da entrega**
- Diário (`pending`) gravado junto com a retirada das chaves; entrega no fim, ao fechar, ao sair, ao desligar; queda
  → entrega no próximo login. Testado: stop no meio, kill -9 no meio (entregue 1 vez no login), fechar o menu, sair.
- Trava por jogador (fila dos comandos de chave) da leitura até a gravação da abertura.
- Animação antiga também entrega ao sair do servidor (antes o prêmio se perdia).
- sgui e fabric-permissions-api dentro do jar (como o 1.0.5 oficial).
