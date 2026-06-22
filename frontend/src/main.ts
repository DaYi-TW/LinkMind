import 'zone.js';
import { bootstrapApplication } from '@angular/platform-browser';
import { AfterViewInit, Component, ElementRef, ViewChild, computed, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import ForceGraph3D from '3d-force-graph';
import * as THREE from 'three';

type NodeType = 'Disease'|'Drug'|'Lab'|'Document'|'Concept';
interface GNode { id:string; label:string; type:NodeType; x:number; y:number; size:number; description:string; docs:number; links:string[] }
interface Edge { id?:string;a:string;b:string;label:string }
interface GraphResponse { nodes:GNode[];edges:Edge[] }
interface AskResponse { answer:string; nodeIds:string[]; path:Edge[] }
interface ChatMsg { who:'ai'|'me'; text:string; nodes?:number; sources?:number }

const NODES:GNode[]=[
 {id:'diabetes',label:'糖尿病',type:'Disease',x:50,y:43,size:34,description:'慢性代謝性疾病，與胰島素分泌或作用異常相關。',docs:28,links:['metformin','hba1c','kidney','insulin']},
 {id:'metformin',label:'Metformin',type:'Drug',x:68,y:28,size:27,description:'第一線口服降血糖藥物，可降低肝臟葡萄糖生成。',docs:12,links:['diabetes','kidney','lactic']},
 {id:'hba1c',label:'HbA1c',type:'Lab',x:31,y:25,size:23,description:'反映近 2–3 個月平均血糖控制狀態的檢驗指標。',docs:17,links:['diabetes','guideline']},
 {id:'kidney',label:'腎功能',type:'Concept',x:76,y:55,size:22,description:'影響 Metformin 用藥評估與劑量調整的重要因素。',docs:9,links:['metformin','lactic']},
 {id:'lactic',label:'乳酸中毒',type:'Disease',x:66,y:74,size:19,description:'少見但嚴重的不良反應，腎功能不全時風險增加。',docs:6,links:['metformin','kidney']},
 {id:'insulin',label:'胰島素阻抗',type:'Concept',x:31,y:66,size:23,description:'第二型糖尿病的重要病理機轉。',docs:21,links:['diabetes','exercise']},
 {id:'guideline',label:'ADA 2025 指引',type:'Document',x:14,y:40,size:19,description:'糖尿病照護標準與臨床決策參考文件。',docs:1,links:['hba1c','diabetes']},
 {id:'exercise',label:'運動介入',type:'Concept',x:43,y:81,size:18,description:'改善胰島素敏感性與代謝指標的非藥物策略。',docs:14,links:['insulin']}
];
const EDGES:Edge[]=[{a:'diabetes',b:'metformin',label:'TREATED_BY'},{a:'diabetes',b:'hba1c',label:'MEASURED_BY'},{a:'metformin',b:'kidney',label:'CAUTION_WITH'},{a:'kidney',b:'lactic',label:'RISK_OF'},{a:'metformin',b:'lactic',label:'MAY_CAUSE'},{a:'diabetes',b:'insulin',label:'RELATED_TO'},{a:'hba1c',b:'guideline',label:'REFERENCED_BY'},{a:'guideline',b:'diabetes',label:'DESCRIBES'},{a:'insulin',b:'exercise',label:'IMPROVED_BY'}];
const NODE_TYPES:NodeType[]=['Disease','Drug','Lab','Document','Concept'];

@Component({selector:'app-root',standalone:true,imports:[CommonModule],templateUrl:'./app.html'})
export class App implements AfterViewInit {
 @ViewChild('graphHost') graphHost?:ElementRef<HTMLDivElement>; private graph:any; private resize?:ResizeObserver;
 private http=inject(HttpClient); private api=(((globalThis as any).__APP_CONFIG__?.apiBaseUrl)||'http://127.0.0.1:8000')+'/api';
 nodeTypes=NODE_TYPES;
 nodes=NODES; edges=EDGES; selected=signal(NODES[1]); query=signal(''); expanded=signal(new Set(NODES.map(n=>n.id))); apiState=signal<'loading'|'online'|'offline'>('loading'); linkMessage=signal(''); nodeMessage=signal(''); asking=signal(false);
 chat=signal<ChatMsg[]>([{who:'ai',text:'知識空間已載入。我可以追蹤實體關聯、比對證據，或整理目前圖譜。'}]);
 highlight=signal<Set<string>>(new Set());
 constructor(){this.loadGraph();effect(()=>{this.query();this.selected();this.highlight();if(this.graph)this.styleGraph()})}
 ngAfterViewInit(){this.initGraph()}
 neuron(n:any){const colors:any={Disease:0xff476f,Drug:0xffb05f,Lab:0x40e8dd,Document:0x6f9cff,Concept:0x8dffc7},color=colors[n.type]??0x8dffc7,active=n.id===this.selected().id,dim=this.isDimmed(n.id);const o=dim?0.12:1;const group=new THREE.Group();group.add(new THREE.Mesh(new THREE.IcosahedronGeometry(active?5.2:3.2,2),new THREE.MeshBasicMaterial({color:active?0xffffff:color,transparent:true,opacity:.96*o})));group.add(new THREE.Mesh(new THREE.IcosahedronGeometry(active?8.4:5.8,1),new THREE.MeshBasicMaterial({color,wireframe:true,transparent:true,opacity:(active?.36:.14)*o,depthWrite:false})));group.add(new THREE.Mesh(new THREE.SphereGeometry(active?11:7,16,16),new THREE.MeshBasicMaterial({color,transparent:true,opacity:(active?.07:.025)*o,depthWrite:false,blending:THREE.AdditiveBlending})));return group}
 // A node is dimmed when a search query (or Copilot highlight) is active and it is not part of the matched set.
 isDimmed(id:string){const q=this.query().trim().toLowerCase(),hl=this.highlight();if(hl.size) return !hl.has(id);if(!q) return false;const n=this.nodes.find(x=>x.id===id);return !n||!(n.label.toLowerCase().includes(q)||n.type.toLowerCase().includes(q)||n.description.toLowerCase().includes(q))}
 linkSeed(l:any){const a=String(l.source?.id??l.source),b=String(l.target?.id??l.target),s=a+'>'+b;return [...s].reduce((v,c)=>(v*31+c.charCodeAt(0))>>>0,7)}
 initGraph(){if(!this.graphHost)return;const el=this.graphHost.nativeElement;this.graph=(ForceGraph3D as any)()(el).backgroundColor('#020706').showNavInfo(false).cooldownTicks(110).cooldownTime(2200).d3VelocityDecay(.48).nodeLabel((n:any)=>`<div class="graph-tooltip"><b>${n.label}</b><span>${n.type} · SYNAPSE</span></div>`).nodeThreeObject((n:any)=>this.neuron(n)).linkWidth((l:any)=>l.source?.id===this.selected().id||l.target?.id===this.selected().id?1.25:.38).linkOpacity(.42).linkCurvature((l:any)=>.12+(this.linkSeed(l)%17)/100).linkCurveRotation((l:any)=>(this.linkSeed(l)%628)/100).linkDirectionalParticles((l:any)=>l.source?.id===this.selected().id||l.target?.id===this.selected().id?4:1).linkDirectionalParticleWidth((l:any)=>l.source?.id===this.selected().id||l.target?.id===this.selected().id?2.2:.7).linkDirectionalParticleSpeed((l:any)=>.002+(this.linkSeed(l)%20)/10000).linkColor((l:any)=>l.source?.id===this.selected().id||l.target?.id===this.selected().id?'#83f6c0':'#29534b').linkDirectionalParticleColor(()=> '#d8fff0').onNodeClick((n:any)=>{this.select(n as GNode);const distance=105,ratio=1+distance/Math.hypot(n.x,n.y,n.z);this.graph.cameraPosition({x:n.x*ratio,y:n.y*ratio,z:n.z*ratio},n,1100)}).onNodeHover((n:any)=>el.style.cursor=n?'pointer':'grab');this.graph.d3Force('charge').strength(-85);this.graph.d3Force('link').distance(52);this.graph.scene().fog=new THREE.FogExp2(0x020706,.0028);this.resize=new ResizeObserver(()=>this.graph.width(el.clientWidth).height(el.clientHeight));this.resize.observe(el);this.renderGraph()}
 renderGraph(){if(!this.graph)return;this.graph.graphData({nodes:this.nodes.map(n=>({...n})),links:this.edges.map(e=>({id:e.id,source:e.a,target:e.b,label:e.label}))});this.styleGraph();setTimeout(()=>this.graph.zoomToFit(700,55),350)}
 styleGraph(){this.graph.nodeThreeObject((n:any)=>this.neuron(n));this.graph.refresh()}
 loadGraph(){this.apiState.set('loading');this.http.get<GraphResponse>(`${this.api}/graph`).subscribe({next:g=>{this.nodes=g.nodes;this.edges=g.edges;this.expanded.set(new Set(g.nodes.map(n=>n.id)));this.selected.set(g.nodes.find(n=>n.id==='metformin')??g.nodes[0]);this.apiState.set('online');this.renderGraph()},error:()=>this.apiState.set('offline')})}

 // --- relations derived from real edges (#4/#8) ---
 neighbours=computed(()=>{const sel=this.selected().id;const out:{nodeId:string;label:string}[]=[];for(const e of this.edges){if(e.a===sel)out.push({nodeId:e.b,label:e.label});else if(e.b===sel)out.push({nodeId:e.a,label:e.label});}return out});
 edgeBetween(a:string,b:string){return this.edges.find(e=>(e.a===a&&e.b===b)||(e.a===b&&e.b===a))}

 // --- links ---
 addLink(target:string,relation:string){this.linkMessage.set('');this.http.post<Edge>(`${this.api}/graph/links`,{sourceId:this.selected().id,targetId:target,relation}).subscribe({next:()=>{this.linkMessage.set('LINK CREATED');this.refresh()},error:e=>this.linkMessage.set(e.error?.message??'Unable to create link')})}
 deleteLink(a:string,b:string){const e=this.edgeBetween(a,b);if(!e?.id)return;this.http.delete(`${this.api}/graph/links/${encodeURIComponent(e.id)}`).subscribe({next:()=>this.refresh(),error:()=>this.linkMessage.set('Unable to delete link')})}

 // --- nodes ---
 addNode(label:string,type:string){this.nodeMessage.set('');if(!label.trim()){this.nodeMessage.set('Label required');return}this.http.post<GNode>(`${this.api}/graph/nodes`,{label,type,description:''}).subscribe({next:n=>{this.nodeMessage.set('NODE CREATED');this.refresh(()=>{const created=this.nodes.find(x=>x.id===n.id);if(created)this.selected.set(created)})},error:e=>this.nodeMessage.set(e.error?.message??'Unable to create node')})}
 deleteNode(id:string){this.http.delete(`${this.api}/graph/nodes/${encodeURIComponent(id)}`).subscribe({next:()=>this.refresh(()=>{if(this.selected().id===id)this.selected.set(this.nodes[0])}),error:()=>this.nodeMessage.set('Unable to delete node')})}

 // reload graph from server, then run an optional callback once state is fresh
 private refresh(after?:()=>void){this.http.get<GraphResponse>(`${this.api}/graph`).subscribe({next:g=>{this.nodes=g.nodes;this.edges=g.edges;const cur=this.nodes.find(n=>n.id===this.selected().id);if(cur)this.selected.set(cur);else if(this.nodes.length)this.selected.set(this.nodes[0]);this.renderGraph();after?.()},error:()=>this.apiState.set('offline')})}

 // --- Copilot: real graph query against the backend (#1) ---
 ask(input:HTMLInputElement|HTMLTextAreaElement){const q=input.value.trim();if(!q||this.asking())return;this.chat.update(c=>[...c,{who:'me',text:q}]);input.value='';this.asking.set(true);this.http.post<AskResponse>(`${this.api}/graph/ask`,{question:q}).subscribe({
  next:r=>{this.asking.set(false);this.chat.update(c=>[...c,{who:'ai',text:r.answer,nodes:r.nodeIds.length,sources:r.path.length}]);this.highlight.set(new Set(r.nodeIds));const first=r.nodeIds.map(id=>this.nodes.find(n=>n.id===id)).find(Boolean);if(first)this.selected.set(first!);},
  error:()=>{this.asking.set(false);this.chat.update(c=>[...c,{who:'ai',text:'抱歉，目前無法連線到知識圖譜服務。'}]);}});}
 clearHighlight(){this.highlight.set(new Set())}

 visible=computed(()=>{const q=this.query().trim().toLowerCase();return new Set(this.nodes.filter(n=>!q||n.label.toLowerCase().includes(q)||n.type.toLowerCase().includes(q)).map(n=>n.id))});
 node(id:string){return this.nodes.find(n=>n.id===id)!} select(n:GNode){if(!n)return;this.selected.set(n);this.clearHighlight();const s=new Set(this.expanded());(n.links||[]).forEach(x=>s.add(x));s.add(n.id);this.expanded.set(s)}
 line(e:Edge){const a=this.node(e.a),b=this.node(e.b);return {x1:a.x+'%',y1:a.y+'%',x2:b.x+'%',y2:b.y+'%'}}
}
bootstrapApplication(App,{providers:[provideHttpClient()]}).catch(console.error);
