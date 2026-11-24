"""Generate editable draw.io diagrams and matching PNG previews (requires Pillow)."""
from pathlib import Path
from xml.etree.ElementTree import Element, SubElement, ElementTree
from PIL import Image, ImageDraw, ImageFont
import math

OUT = Path(__file__).resolve().parent
FONT = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 18)
SMALL = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 15)
TITLE = ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf', 25)

# Each box is (id, text, x, y, width, height); edges are (source, target, label).
DIAGRAMS = {
'context': ('Контекст Cloud-Comment', [('owner','Владелец сайта',40,100,200,80),('system','Cloud-Comment\nпанель + API + виджет',350,90,280,100),('visitor','Посетитель сайта',760,100,200,80),('site','Внешний сайт\nс виджетом',760,280,200,80),('mail','Почтовая служба\nFR-9: планируется',350,280,280,80)], [('owner','system','управляет'),('visitor','system','комментирует'),('site','system','встраивает'),('system','mail','план: письмо')]),
'activity': ('Основной процесс · US-2—US-6', [('start','Владелец добавляет сайт',50,80,260,65),('code','Получает код FR-3',370,80,250,65),('install','Размещает виджет',690,80,250,65),('read','Посетитель открывает страницу',690,240,250,65),('send','Отправляет комментарий',370,240,250,65),('review','Владелец модерирует',50,240,260,65),('result','Результат виден по статусу',370,390,250,65)], [('start','code',''),('code','install',''),('install','read',''),('read','send',''),('send','review',''),('review','result','')]),
'use-cases': ('Варианты использования · FR-1—FR-9', [('owner','Владелец',20,215,140,70),('a','FR-1 Регистрация / вход',240,55,300,48),('b','FR-2 Добавить сайт',240,120,300,48),('c','FR-3 Код виджета',240,185,300,48),('d','FR-6 Модерация',240,250,300,48),('e','FR-7 Ответ владельца',240,315,300,48),('f','FR-8 Настройка стиля',240,380,300,48),('g','FR-9 Восстановление · план',240,445,300,48),('visitor','Посетитель',800,215,160,70),('h','FR-4 Просмотр',600,75,160,55),('i','FR-5 Отправка',600,145,160,55)], [('owner','a',''),('owner','b',''),('owner','c',''),('owner','d',''),('owner','e',''),('owner','f',''),('owner','g',''),('visitor','h',''),('visitor','i','')]),
'sequence-login': ('Последовательность · вход US-1', [('u','Владелец',40,70,180,60),('ui','Панель',290,70,180,60),('api','API авторизации',540,70,180,60),('db','PostgreSQL',790,70,170,60),('s1','1. Email и пароль',40,220,180,60),('s2','2. Запрос входа',290,220,180,60),('s3','3. Проверка данных',540,220,180,60),('s4','4. Учётная запись',790,220,170,60),('s5','5. Сессия → панель',290,360,430,60)], [('u','s1',''),('s1','s2',''),('s2','s3',''),('s3','s4',''),('s4','s5','ответ'),('s5','u','')]),
'sequence-comment': ('Последовательность · комментарий US-5', [('u','Посетитель',30,80,190,65),('w','Виджет',270,80,190,65),('api','API комментариев',510,80,200,65),('db','PostgreSQL',760,80,200,65),('s','1. Текст и отправка',30,210,190,65),('v','2. Проверка данных',510,210,200,65),('err','Ошибка: HTTP 400\nVALIDATION_FAILED',270,340,220,75),('ok','Успех: сохранить\nи вернуть результат',760,340,200,75)], [('u','s',''),('s','w',''),('w','v','запрос'),('v','err','пустой текст'),('err','u','сообщение'),('v','ok','корректно'),('ok','w','результат')]),
'domain': ('Концептуальная модель данных', [('user','Пользователь\nid, email, роль',30,80,220,85),('site','Сайт\nid, имя, домен',390,80,220,85),('discussion','Обсуждение\nsiteId, pageUrl',750,80,220,85),('comment','Комментарий\nid, текст, статус',750,300,220,85),('action','Действие модерации\nid, действие, время',390,300,220,85)], [('user','site','1 → 0..*'),('site','discussion','1 → 0..*'),('discussion','comment','1 → 0..*'),('user','comment','автор 0..*'),('comment','action','1 → 0..*'),('user','action','исполнитель')]),
'states': ('Состояния комментария · FR-5, FR-6', [('new','Создание',30,200,150,65),('pending','PENDING\nожидает',260,70,180,75),('approved','APPROVED\nопубликован',530,70,190,75),('rejected','REJECTED\nотклонён',260,310,180,75),('hidden','HIDDEN\nскрыт',530,310,190,75),('spam','SPAM\nспам',790,200,170,75)], [('new','pending','премодерация'),('new','approved','публикация'),('pending','approved','одобрить'),('pending','rejected','отклонить'),('approved','hidden','скрыть'),('hidden','approved','восстановить'),('approved','spam','спам'),('spam','approved','восстановить')])
}

def center(b): return (b[2]+b[4]/2, b[3]+b[5]/2)
def wrapped(draw, text, font, width):
    lines=[]
    for part in text.split('\n'):
        line=''
        for word in part.split():
            trial=(line+' '+word).strip()
            if line and draw.textlength(trial,font=font)>width: lines.append(line); line=word
            else: line=trial
        lines.append(line)
    return '\n'.join(lines)

def build(name, title, boxes, edges):
    lookup={b[0]:b for b in boxes}
    width=max(1000,max(b[2]+b[4] for b in boxes)+35); height=max(500,max(b[3]+b[5] for b in boxes)+55)
    image=Image.new('RGB',(width,height),'white'); d=ImageDraw.Draw(image)
    d.text((25,18),title,font=TITLE,fill='#172b4d')
    mx=Element('mxfile',host='app.diagrams.net'); dia=SubElement(mx,'diagram',name=title); model=SubElement(dia,'mxGraphModel',dx=str(width),dy=str(height),grid='1',gridSize='10',page='1'); root=SubElement(model,'root'); SubElement(root,'mxCell',id='0'); SubElement(root,'mxCell',id='1',parent='0')
    for idx,(src,dst,label) in enumerate(edges):
        a,b=lookup[src],lookup[dst]; x1,y1=center(a); x2,y2=center(b); dx=x2-x1; dy=y2-y1
        scale1=min((a[4]/2)/(abs(dx) or 0.001),(a[5]/2)/(abs(dy) or 0.001)); scale2=min((b[4]/2)/(abs(dx) or 0.001),(b[5]/2)/(abs(dy) or 0.001))
        p=(x1+dx*scale1,y1+dy*scale1); q=(x2-dx*scale2,y2-dy*scale2); d.line([p,q],fill='#52677f',width=2)
        angle=math.atan2(q[1]-p[1],q[0]-p[0]); d.polygon([q,(q[0]-12*math.cos(angle-.4),q[1]-12*math.sin(angle-.4)),(q[0]-12*math.cos(angle+.4),q[1]-12*math.sin(angle+.4))],fill='#52677f')
        if label: d.text(((p[0]+q[0])/2,(p[1]+q[1])/2-18),label,font=SMALL,fill='#213c5a',stroke_width=2,stroke_fill='white')
        cell=SubElement(root,'mxCell',id='e'+str(idx),value=label,style='edgeStyle=none;endArrow=classic;html=1;',edge='1',parent='1',source=src,target=dst); SubElement(cell,'mxGeometry',relative='1',attrib={'as':'geometry'})
    for ident,text,x,y,w,h in boxes:
        d.rounded_rectangle((x,y,x+w,y+h),radius=10,fill='#edf5ff',outline='#3a6da8',width=2)
        text=wrapped(d,text,FONT,w-16); bounds=d.multiline_textbbox((0,0),text,font=FONT,align='center',spacing=4); d.multiline_text((x+(w-(bounds[2]-bounds[0]))/2,y+(h-(bounds[3]-bounds[1]))/2-3),text,font=FONT,fill='#172b4d',align='center',spacing=4)
        cell=SubElement(root,'mxCell',id=ident,value=text.replace('\n','&#xa;'),style='rounded=1;whiteSpace=wrap;html=1;fillColor=#edf5ff;strokeColor=#3a6da8;fontSize=16;',vertex='1',parent='1'); SubElement(cell,'mxGeometry',x=str(x),y=str(y),width=str(w),height=str(h),attrib={'as':'geometry'})
    image.save(OUT/(name+'.png')); ElementTree(mx).write(OUT/(name+'.drawio'),encoding='utf-8',xml_declaration=True)

for name,(title,boxes,edges) in DIAGRAMS.items(): build(name,title,boxes,edges)

def sequence(name, title, participants, messages):
    width=1100; height=150+len(messages)*65
    im=Image.new('RGB',(width,height),'white'); d=ImageDraw.Draw(im); d.text((25,15),title,font=TITLE,fill='#172b4d')
    mx=Element('mxfile',host='app.diagrams.net'); dia=SubElement(mx,'diagram',name=title); model=SubElement(dia,'mxGraphModel',page='1',grid='1'); root=SubElement(model,'root'); SubElement(root,'mxCell',id='0'); SubElement(root,'mxCell',id='1',parent='0')
    xs=[120+i*270 for i in range(len(participants))]
    for i,(x,label) in enumerate(zip(xs,participants)):
        d.rounded_rectangle((x-90,65,x+90,120),radius=8,fill='#edf5ff',outline='#3a6da8',width=2); d.text((x,82),label,font=FONT,anchor='mt',fill='#172b4d'); d.line((x,120,x,height-25),fill='#8b9caf',width=2)
        cell=SubElement(root,'mxCell',id=f'p{i}',value=label,style='rounded=1;whiteSpace=wrap;html=1;fillColor=#edf5ff;',vertex='1',parent='1'); SubElement(cell,'mxGeometry',x=str(x-90),y='65',width='180',height='55',attrib={'as':'geometry'})
        line=SubElement(root,'mxCell',id=f'l{i}',value='',style='endArrow=none;dashed=1;',edge='1',parent='1'); geo=SubElement(line,'mxGeometry',relative='1',attrib={'as':'geometry'}); SubElement(geo,'mxPoint',x=str(x),y='120',attrib={'as':'sourcePoint'}); SubElement(geo,'mxPoint',x=str(x),y=str(height-25),attrib={'as':'targetPoint'})
    for i,(a,b,label) in enumerate(messages):
        y=155+i*65; x1=xs[a]; x2=xs[b]; d.line((x1,y,x2,y),fill='#344e6b',width=2); sign=1 if x2>x1 else -1; d.polygon([(x2,y),(x2-sign*12,y-6),(x2-sign*12,y+6)],fill='#344e6b'); d.text(((x1+x2)/2,y-7),label,font=SMALL,anchor='ms',fill='#172b4d')
        cell=SubElement(root,'mxCell',id=f'm{i}',value=label,style='endArrow=classic;html=1;',edge='1',parent='1'); geo=SubElement(cell,'mxGeometry',relative='1',attrib={'as':'geometry'}); SubElement(geo,'mxPoint',x=str(x1),y=str(y),attrib={'as':'sourcePoint'}); SubElement(geo,'mxPoint',x=str(x2),y=str(y),attrib={'as':'targetPoint'})
    im.save(OUT/(name+'.png')); ElementTree(mx).write(OUT/(name+'.drawio'),encoding='utf-8',xml_declaration=True)

sequence('sequence-login','Последовательность входа · US-1',['Владелец','Панель','API','База данных'],[(0,1,'1. Email и пароль'),(1,2,'2. Запрос входа'),(2,3,'3. Найти пользователя'),(3,2,'4. Данные учётной записи'),(2,3,'5. Создать сессию'),(2,1,'6. Успех и cookie'),(1,0,'7. Открыть панель')])
sequence('sequence-comment','Отправка комментария и ошибка · US-5',['Посетитель','Виджет','API','База данных'],[(0,1,'1. Ввести текст и отправить'),(1,2,'2. Отправить запрос'),(2,1,'3a. Пустой текст: HTTP 400, VALIDATION_FAILED'),(1,0,'4a. Показать ошибку; не создан'),(2,3,'3b. Корректный текст: сохранить'),(3,2,'4b. Сохранённый комментарий'),(2,1,'5b. Результат с учётом модерации'),(1,0,'6b. Показать результат')])
