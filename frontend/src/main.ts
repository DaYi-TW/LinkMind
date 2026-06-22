import 'zone.js';
import { bootstrapApplication } from '@angular/platform-browser';
import { AfterViewInit, Component, ElementRef, ViewChild, computed, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import ForceGraph3D from '3d-force-graph';
import * as THREE from 'three';

type NodeType = 'Concept'|'Person'|'Place'|'Event'|'Work'|'Source'|'Idea';
interface GNode { id:string; label:string; type:NodeType; x:number; y:number; size:number; description:string; docs:number; links:string[] }
interface Edge { id?:string;a:string;b:string;label:string }
interface GraphResponse { nodes:GNode[];edges:Edge[] }
interface AskResponse { answer:string; nodeIds:string[]; path:Edge[] }
interface ChatMsg { who:'ai'|'me'; text:string; nodes?:number; sources?:number }

const NODE_TYPES:NodeType[]=['Concept','Person','Place','Event','Work','Source','Idea'];
// node-type → hex, kept in sync with the CSS taxonomy variables in styles.css
const TYPE_COLOR:Record<NodeType,number>={Concept:0x5b8def,Person:0xe0764e,Place:0x3fae8e,Event:0xc264c8,Work:0xd9a226,Source:0x8a7de0,Idea:0xe05b78};
// Local fallback graph (only shown if the backend is unreachable) — mirrors the server seed.
const NODES:GNode[]=[
 {id:'knowledge-graph',label:'知識圖譜',type:'Concept',x:50,y:46,size:34,description:'用節點與關聯來組織知識的方法，是這個工具的核心概念。',docs:12,links:['second-brain','note-taking','zettelkasten','graph-theory']},
 {id:'second-brain',label:'第二大腦',type:'Idea',x:70,y:30,size:27,description:'把記憶與思考外部化到數位系統，讓大腦專注於連結與創造。',docs:9,links:['knowledge-graph','note-taking']},
 {id:'zettelkasten',label:'Zettelkasten',type:'Concept',x:30,y:28,size:24,description:'卡片盒筆記法：每則筆記原子化並彼此連結。',docs:7,links:['knowledge-graph','note-taking']},
 {id:'note-taking',label:'筆記法',type:'Concept',x:52,y:68,size:23,description:'擷取與組織想法的實踐；連結式筆記是知識圖譜的基礎。',docs:11,links:['knowledge-graph','obsidian']},
 {id:'obsidian',label:'Obsidian',type:'Work',x:78,y:58,size:21,description:'以雙向連結與本地 Markdown 著稱的筆記軟體。',docs:5,links:['note-taking']}
];
const EDGES:Edge[]=[{a:'knowledge-graph',b:'second-brain',label:'ENABLES'},{a:'knowledge-graph',b:'zettelkasten',label:'INSPIRED_BY'},{a:'knowledge-graph',b:'note-taking',label:'BUILT_ON'},{a:'note-taking',b:'obsidian',label:'TOOL_FOR'}];

@Component({selector:'app-root',standalone:true,imports:[CommonModule],templateUrl:'./app.html'})
export class App implements AfterViewInit {
 @ViewChild('graphHost') graphHost?:ElementRef<HTMLDivElement>; private graph:any; private resize?:ResizeObserver;
 private http=inject(HttpClient); private api=(((globalThis as any).__APP_CONFIG__?.apiBaseUrl)||'http://127.0.0.1:8000')+'/api';
 nodeTypes=NODE_TYPES;
 nodes=NODES; edges=EDGES; selected=signal<GNode|null>(null); query=signal(''); expanded=signal(new Set(NODES.map(n=>n.id))); apiState=signal<'loading'|'online'|'offline'>('loading'); linkMessage=signal(''); nodeMessage=signal(''); asking=signal(false);
 chat=signal<ChatMsg[]>([{who:'ai',text:'歡迎來到你的知識圖譜。問我任何問題，我會在圖譜中追蹤相關的概念與關聯。'}]);
 highlight=signal<Set<string>>(new Set());
 rail=signal(false);            // left Copilot rail collapsed?
 theme=signal<'dark'|'light'>('dark');
 constructor(){
  const saved=(globalThis as any).localStorage?.getItem('atlas-theme'); if(saved==='light'||saved==='dark')this.theme.set(saved);
  this.applyTheme();
  this.loadGraph();effect(()=>{this.query();this.selected();this.highlight();if(this.graph)this.styleGraph()})}
 ngAfterViewInit(){this.initGraph()}
 toggleTheme(){this.theme.set(this.theme()==='dark'?'light':'dark');this.applyTheme();(globalThis as any).localStorage?.setItem('atlas-theme',this.theme());if(this.graph){this.graph.backgroundColor(this.canvasColor());this.styleGraph()}}
 private applyTheme(){(globalThis as any).document?.documentElement?.setAttribute('data-theme',this.theme())}
 private canvasColor(){return this.theme()==='dark'?'#080b11':'#f7f5ef'}
 focusCreate(){setTimeout(()=>{const el=(globalThis as any).document?.querySelector('.field input') as HTMLInputElement|undefined;el?.focus();el?.scrollIntoView({block:'center'})},80)}
 neuron(n:any){const color=TYPE_COLOR[n.type as NodeType]??0x5b8def,active=n.id===this.selected()?.id,dim=this.isDimmed(n.id);const o=dim?0.1:1;const core=this.theme()==='light'?0x2a2620:0xffffff;const group=new THREE.Group();group.add(new THREE.Mesh(new THREE.IcosahedronGeometry(active?5.2:3.2,2),new THREE.MeshBasicMaterial({color:active?core:color,transparent:true,opacity:.96*o})));group.add(new THREE.Mesh(new THREE.IcosahedronGeometry(active?8.4:5.8,1),new THREE.MeshBasicMaterial({color,wireframe:true,transparent:true,opacity:(active?.42:.16)*o,depthWrite:false})));group.add(new THREE.Mesh(new THREE.SphereGeometry(active?11:7,16,16),new THREE.MeshBasicMaterial({color,transparent:true,opacity:(active?.09:.03)*o,depthWrite:false,blending:THREE.AdditiveBlending})));return group}
 // A node is dimmed when a search query (or Copilot highlight) is active and it is not part of the matched set.
 isDimmed(id:string){const q=this.query().trim().toLowerCase(),hl=this.highlight();if(hl.size) return !hl.has(id);if(!q) return false;const n=this.nodes.find(x=>x.id===id);return !n||!(n.label.toLowerCase().includes(q)||n.type.toLowerCase().includes(q)||n.description.toLowerCase().includes(q))}
 linkSeed(l:any){const a=String(l.source?.id??l.source),b=String(l.target?.id??l.target),s=a+'>'+b;return [...s].reduce((v,c)=>(v*31+c.charCodeAt(0))>>>0,7)}
 private isOn(l:any){const s=this.selected()?.id;return s!=null&&(l.source?.id===s||l.target?.id===s)}
 initGraph(){if(!this.graphHost)return;const el=this.graphHost.nativeElement;this.graph=(ForceGraph3D as any)()(el).backgroundColor(this.canvasColor()).showNavInfo(false).cooldownTicks(120).cooldownTime(2600).d3VelocityDecay(.46).nodeLabel((n:any)=>`<div class="graph-tooltip"><b>${n.label}</b><span>${n.type}</span></div>`).nodeThreeObject((n:any)=>this.neuron(n)).linkWidth((l:any)=>this.isOn(l)?1.6:.4).linkOpacity(this.theme()==='light'?.3:.4).linkCurvature((l:any)=>.1+(this.linkSeed(l)%17)/120).linkCurveRotation((l:any)=>(this.linkSeed(l)%628)/100).linkDirectionalParticles((l:any)=>this.isOn(l)?4:0).linkDirectionalParticleWidth(()=>2).linkDirectionalParticleSpeed((l:any)=>.003+(this.linkSeed(l)%20)/9000).linkColor((l:any)=>this.isOn(l)?'#e0a458':(this.theme()==='light'?'#c9bfa8':'#2a3445')).linkDirectionalParticleColor(()=>'#e0a458').onNodeClick((n:any)=>{this.select(n as GNode);const distance=110,ratio=1+distance/Math.hypot(n.x,n.y,n.z);this.graph.cameraPosition({x:n.x*ratio,y:n.y*ratio,z:n.z*ratio},n,1100)}).onBackgroundClick(()=>this.selected.set(null)).onNodeHover((n:any)=>el.style.cursor=n?'pointer':'grab');this.graph.d3Force('charge').strength(-90);this.graph.d3Force('link').distance(54);this.resize=new ResizeObserver(()=>this.graph.width(el.clientWidth).height(el.clientHeight));this.resize.observe(el);this.renderGraph()}
 renderGraph(){if(!this.graph)return;this.graph.graphData({nodes:this.nodes.map(n=>({...n})),links:this.edges.map(e=>({id:e.id,source:e.a,target:e.b,label:e.label}))});this.styleGraph();setTimeout(()=>this.graph.zoomToFit(700,55),350)}
 styleGraph(){if(!this.graph)return;this.graph.nodeThreeObject((n:any)=>this.neuron(n)).linkColor((l:any)=>this.isOn(l)?'#e0a458':(this.theme()==='light'?'#c9bfa8':'#2a3445')).linkWidth((l:any)=>this.isOn(l)?1.6:.4).linkOpacity(this.theme()==='light'?.3:.4).linkDirectionalParticles((l:any)=>this.isOn(l)?4:0).backgroundColor(this.canvasColor());this.graph.refresh()}
 loadGraph(){this.apiState.set('loading');this.http.get<GraphResponse>(`${this.api}/graph`).subscribe({next:g=>{this.nodes=g.nodes;this.edges=g.edges;this.expanded.set(new Set(g.nodes.map(n=>n.id)));this.apiState.set('online');this.renderGraph()},error:()=>this.apiState.set('offline')})}

 // --- relations derived from real edges (#4/#8) ---
 neighbours=computed(()=>{const sel=this.selected()?.id;const out:{nodeId:string;label:string}[]=[];if(!sel)return out;for(const e of this.edges){if(e.a===sel)out.push({nodeId:e.b,label:e.label});else if(e.b===sel)out.push({nodeId:e.a,label:e.label});}return out});
 edgeBetween(a:string,b:string){return this.edges.find(e=>(e.a===a&&e.b===b)||(e.a===b&&e.b===a))}

 // --- links ---
 addLink(target:string,relation:string){const src=this.selected()?.id;if(!src)return;this.linkMessage.set('');this.http.post<Edge>(`${this.api}/graph/links`,{sourceId:src,targetId:target,relation}).subscribe({next:()=>{this.linkMessage.set('已建立關聯');this.refresh()},error:e=>this.linkMessage.set(e.error?.message??'無法建立關聯')})}
 deleteLink(a:string,b:string){const e=this.edgeBetween(a,b);if(!e?.id)return;this.http.delete(`${this.api}/graph/links/${encodeURIComponent(e.id)}`).subscribe({next:()=>this.refresh(),error:()=>this.linkMessage.set('Unable to delete link')})}

 // --- nodes ---
 addNode(label:string,type:string){this.nodeMessage.set('');if(!label.trim()){this.nodeMessage.set('請輸入名稱');return}this.http.post<GNode>(`${this.api}/graph/nodes`,{label,type,description:''}).subscribe({next:n=>{this.nodeMessage.set('已建立節點');this.refresh(()=>{const created=this.nodes.find(x=>x.id===n.id);if(created)this.selected.set(created)})},error:e=>this.nodeMessage.set(e.error?.message??'無法建立節點')})}
 deleteNode(id:string){this.http.delete(`${this.api}/graph/nodes/${encodeURIComponent(id)}`).subscribe({next:()=>this.refresh(()=>{if(this.selected()?.id===id)this.selected.set(null)}),error:()=>this.nodeMessage.set('無法刪除節點')})}

 // reload graph from server, then run an optional callback once state is fresh
 private refresh(after?:()=>void){this.http.get<GraphResponse>(`${this.api}/graph`).subscribe({next:g=>{this.nodes=g.nodes;this.edges=g.edges;const cur=this.nodes.find(n=>n.id===this.selected()?.id);this.selected.set(cur??null);this.renderGraph();after?.()},error:()=>this.apiState.set('offline')})}

 // --- Copilot: real graph query against the backend (#1) ---
 ask(input:HTMLInputElement|HTMLTextAreaElement){const q=input.value.trim();if(!q||this.asking())return;this.chat.update(c=>[...c,{who:'me',text:q}]);input.value='';this.asking.set(true);this.http.post<AskResponse>(`${this.api}/graph/ask`,{question:q}).subscribe({
  next:r=>{this.asking.set(false);this.chat.update(c=>[...c,{who:'ai',text:r.answer,nodes:r.nodeIds.length,sources:r.path.length}]);this.highlight.set(new Set(r.nodeIds));const first=r.nodeIds.map(id=>this.nodes.find(n=>n.id===id)).find(Boolean);if(first)this.selected.set(first!);},
  error:()=>{this.asking.set(false);this.chat.update(c=>[...c,{who:'ai',text:'抱歉，目前無法連線到圖譜服務。'}]);}});}
 clearHighlight(){this.highlight.set(new Set())}

 visible=computed(()=>{const q=this.query().trim().toLowerCase();return new Set(this.nodes.filter(n=>!q||n.label.toLowerCase().includes(q)||n.type.toLowerCase().includes(q)).map(n=>n.id))});
 node(id:string){return this.nodes.find(n=>n.id===id)!} select(n:GNode){if(!n)return;this.selected.set(n);this.clearHighlight();const s=new Set(this.expanded());(n.links||[]).forEach(x=>s.add(x));s.add(n.id);this.expanded.set(s)}
 line(e:Edge){const a=this.node(e.a),b=this.node(e.b);return {x1:a.x+'%',y1:a.y+'%',x2:b.x+'%',y2:b.y+'%'}}
}
bootstrapApplication(App,{providers:[provideHttpClient()]}).catch(console.error);
