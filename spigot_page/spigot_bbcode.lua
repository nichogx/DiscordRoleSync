-- Pandoc filter for SpigotMC's BBCode, used with `-t bbcode_xenforo`.
-- Spigot doesn't support [heading=N] or [img alt=...], so replace them with tags it does support.

-- Same as include_badges in ci/common.py, so all platforms show the same badges
local include_badges = { "Discord", "GitLab Issues" }

local heading_styles = {
  [1] = { "[B][SIZE=7]", "[/SIZE][/B]" },
  [2] = { "[B][SIZE=6]", "[/SIZE][/B]" },
  [3] = { "[B][SIZE=5]", "[/SIZE][/B]" },
  [4] = { "[B][SIZE=4][COLOR=#808080]", "[/COLOR][/SIZE][/B]" },
}

local function raw(text)
  return pandoc.RawInline("bbcode", text)
end

local function is_badge(el)
  return el.t == "Link" and #el.content == 1 and el.content[1].t == "Image"
end

local function should_include_badge(badge)
  local alt = pandoc.utils.stringify(badge.content[1].caption)
  for _, name in ipairs(include_badges) do
    if alt:sub(1, #name) == name then
      return true
    end
  end
  return false
end

local function is_whitespace(el)
  return el.t == "Space" or el.t == "SoftBreak" or el.t == "LineBreak"
end

-- Removes badges not in include_badges from paragraphs made only of badges.
-- Badges inline with other text are left alone, like in ci/common.py.
local function remove_badges(para)
  local badges = pandoc.Inlines({})
  for _, el in ipairs(para.content) do
    if is_badge(el) then
      if should_include_badge(el) then
        if #badges > 0 then
          badges:insert(pandoc.Space())
        end
        badges:insert(el)
      end
    elseif not is_whitespace(el) then
      return nil
    end
  end

  if #badges == 0 then
    return {}
  end
  return pandoc.Para(badges)
end

local function header(el)
  local style = heading_styles[el.level] or heading_styles[4]
  local content = pandoc.Inlines({ raw(style[1]) })
  content:extend(el.content)
  content:insert(raw(style[2]))
  return pandoc.Para(content)
end

local function image(el)
  return raw("[IMG]" .. el.src .. "[/IMG]")
end

-- Badges must be removed before images are converted to raw BBCode
return {
  { Para = remove_badges },
  { Header = header, Image = image },
}
