# Changelog (fork da Pokémania)

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

**ManiaUI**: preview, abertura e lista com Pokémon em 3D; app "Caixas" no Pokélógio.

**Segurança da entrega**
- Diário (`pending`) gravado junto com a retirada das chaves; entrega no fim, ao fechar, ao sair, ao desligar; queda
  → entrega no próximo login. Testado: stop no meio, kill -9 no meio (entregue 1 vez no login), fechar o menu, sair.
- Trava por jogador (fila dos comandos de chave) da leitura até a gravação da abertura.
- Animação antiga também entrega ao sair do servidor (antes o prêmio se perdia).
- sgui e fabric-permissions-api dentro do jar (como o 1.0.5 oficial).
