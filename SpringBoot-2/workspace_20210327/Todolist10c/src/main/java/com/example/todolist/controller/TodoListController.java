package com.example.todolist.controller;

import java.util.Locale;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpSession;

import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.todolist.common.OpMsg;
import com.example.todolist.dao.TodoDaoImpl;
import com.example.todolist.entity.Task;
import com.example.todolist.entity.Todo;
import com.example.todolist.form.TodoData;
import com.example.todolist.form.TodoQuery;
import com.example.todolist.repository.TaskRepository;
import com.example.todolist.repository.TodoRepository;
import com.example.todolist.service.TodoService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class TodoListController {
    private final TodoRepository todoRepository;
    private final TodoService todoService;
    private final HttpSession session;
    private final MessageSource messageSource;
    private final TaskRepository taskRepository;

    @PersistenceContext
    private EntityManager entityManager;
    TodoDaoImpl todoDaoImpl;

    @PostConstruct
    public void init() {
        todoDaoImpl = new TodoDaoImpl(entityManager);
    }

    // ToDo一覧表示
    @GetMapping("/todo")
    public ModelAndView showTodoList(ModelAndView mv,
                                     @PageableDefault(page = 0, size = 5, sort = "id") Pageable pageable) {
        // sessionから前回の検索条件を取得
        TodoQuery todoQuery = (TodoQuery)session.getAttribute("todoQuery");
        if (todoQuery == null) {
            // なければ初期値を使う
            todoQuery = new TodoQuery();
            session.setAttribute("todoQuery", todoQuery);
        }

        // sessionから前回のpageableを取得
        Pageable prevPageable = (Pageable)session.getAttribute("prevPageable");
        if (prevPageable == null) {
            // なければ@PageableDefaultを使う
            prevPageable = pageable;
            session.setAttribute("prevPageable", prevPageable);
        }

        mv.setViewName("todoList");

        // Todo検索
        Page<Todo> todoPage = todoDaoImpl.findByCriteria(todoQuery, prevPageable);
        mv.addObject("todoQuery", todoQuery); // 検索条件
        mv.addObject("todoPage", todoPage); // page情報
        mv.addObject("todoList", todoPage.getContent()); // 検索結果

        return mv;
    }

    // ToDo表示
    @GetMapping("/todo/{id}")
    public ModelAndView todoById(@PathVariable(name = "id") int id, ModelAndView mv) {
        mv.setViewName("todoForm");
        Todo todo = todoRepository.findById(id).get();
        mv.addObject("todoData", new TodoData(todo)); 
        session.setAttribute("mode", "update");
        return mv;
    }

    // ToDo入力フォーム表示
    @PostMapping("/todo/create/form")
    public ModelAndView createTodo(ModelAndView mv) {
        mv.setViewName("todoForm");
        mv.addObject("todoData", new TodoData());
        session.setAttribute("mode", "create");
        return mv;
    }

    // ToDo追加処理
    @PostMapping("/todo/create/do")
    public String createTodo(@ModelAttribute @Validated TodoData todoData, BindingResult result,
                             Model model, RedirectAttributes redirectAttributes, Locale locale) {
        // エラーチェック
        boolean isValid = todoService.isValid(todoData, result, true, locale);
        if (!result.hasErrors() && isValid) {
            // エラーなし -> 追加
            Todo todo = todoData.toEntity();
            todoRepository.saveAndFlush(todo);

            // 追加完了メッセージをセットしてリダイレクト
            String msg = messageSource.getMessage("msg.i.todo_created", null, locale);
            redirectAttributes.addFlashAttribute("msg", new OpMsg("I", msg));
            return "redirect:/todo/" + todo.getId(); 

        } else {
            // エラーあり -> エラーメッセージをセット
            String msg = messageSource.getMessage("msg.e.input_something_wrong", null, locale);
            model.addAttribute("msg", new OpMsg("E", msg));
            return "todoForm";
        }
    }

    // ToDo更新処理
    @PostMapping("/todo/update")
    public String updateTodo(@ModelAttribute @Validated TodoData todoData, BindingResult result,
                             Model model, RedirectAttributes redirectAttributes, Locale locale) {
        // エラーチェック
        boolean isValid = todoService.isValid(todoData, result, false, locale);
        if (!result.hasErrors() && isValid) {
            // エラーなし -> 更新
            Todo todo = todoData.toEntity();
            todoRepository.saveAndFlush(todo);

            // 更新完了メッセージをセットしてリダイレクト
            String msg = messageSource.getMessage("msg.i.todo_updated", null, locale);
            redirectAttributes.addFlashAttribute("msg", new OpMsg("I", msg));

            return "redirect:/todo/" + todo.getId();

        } else {
            // エラーあり -> エラーメッセージをセット
            String msg = messageSource.getMessage("msg.e.input_something_wrong", null, locale);
            model.addAttribute("msg", new OpMsg("E", msg));
            return "todoForm";
        }
    }

    // ToDo削除処理
    @PostMapping("/todo/delete")
    public String deleteTodo(@ModelAttribute TodoData todoData,
                             RedirectAttributes redirectAttributes, Locale locale) {
        // 削除
        todoRepository.deleteById(todoData.getId());

        // 削除完了メッセージをセットしてリダイレクト
        String msg = messageSource.getMessage("msg.i.todo_deleted", null, locale);
        redirectAttributes.addFlashAttribute("msg", new OpMsg("I", msg));
        return "redirect:/todo";
    }

    // ToDo検索処理
    @PostMapping("/todo/query")
    public ModelAndView queryTodo(@ModelAttribute TodoQuery todoQuery, BindingResult result,
                                  @PageableDefault(page = 0, size = 5, sort = "id") Pageable pageable,
                                  ModelAndView mv, Locale locale) {
        mv.setViewName("todoList");

        Page<Todo> todoPage = null;
        if (todoService.isValid(todoQuery, result, locale)) {
            // エラーがなければ検索
            todoPage = todoDaoImpl.findByCriteria(todoQuery, pageable);

            // 入力された検索条件をsessionへ保存
            session.setAttribute("todoQuery", todoQuery);

            mv.addObject("todoPage", todoPage);
            mv.addObject("todoList", todoPage.getContent());

            // 該当なかったらメッセージを表示
            if (todoPage.getContent().size() == 0) {
                String msg = messageSource.getMessage("msg.w.todo_not_found", null, locale);
                mv.addObject("msg", new OpMsg("W", msg));
            }

        } else {
            // 検索条件エラーあり -> エラーメッセージをセット
            String msg = messageSource.getMessage("msg.e.input_something_wrong", null, locale);
            mv.addObject("msg", new OpMsg("E", msg));

            mv.addObject("todoPage", null);
            mv.addObject("todoList", null);
        }

        // mv.addObject("todoQuery", todoQuery);
        return mv;
    }

    // ページリンク押下時
    @GetMapping("/todo/query")
    public ModelAndView queryTodo(@PageableDefault(page = 0, size = 5, sort = "id") Pageable pageable,
                                  ModelAndView mv) {
        // 現在のページ位置を保存
        session.setAttribute("prevPageable", pageable);

        mv.setViewName("todoList");

        // sessionに保存されている条件で検索
        TodoQuery todoQuery = (TodoQuery)session.getAttribute("todoQuery");
        Page<Todo> todoPage = todoDaoImpl.findByCriteria(todoQuery, pageable);

        mv.addObject("todoQuery", todoQuery); // 検索条件
        mv.addObject("todoPage", todoPage); // page情報
        mv.addObject("todoList", todoPage.getContent()); // 検索結果

        return mv;
    }

    // ToDo一覧へ戻る
    @PostMapping("/todo/cancel")
    public String cancel() {
        return "redirect:/todo";
    }
    
    // Task削除処理
    @GetMapping("/task/delete")
    public String deleteTask(@RequestParam(name = "task_id") int taskId,
                              @RequestParam(name = "todo_id") int todoId, 
                              RedirectAttributes redirectAttributes, Locale locale) {
        // Taskを削除
        taskRepository.deleteById(taskId);

        // 削除完了メッセージをセットしてリダイレクト
        String msg = messageSource.getMessage("msg.i.task_deleted", null, locale);
        redirectAttributes.addFlashAttribute("msg", new OpMsg("I", msg));
        return "redirect:/todo/" + todoId;
    }
    
    // Task追加処理
    @PostMapping("/task/create")
    public String createTask(@ModelAttribute TodoData todoData,
                               BindingResult result, Model model,
                               RedirectAttributes redirectAttributes, Locale locale) {
        // エラーチェック
        boolean isValid = todoService.isValid(todoData.getNewTask(), result, locale);
        if (isValid) {
            // エラーなし
            Todo todo = todoData.toEntity();
            Task task = todoData.toTaskEntity();
            task.setTodo(todo);
            taskRepository.saveAndFlush(task);
  
            // 追加完了メッセージをセットしてリダイレクト
            String msg = messageSource.getMessage("msg.i.task_created", null, locale);
            redirectAttributes.addFlashAttribute("msg", new OpMsg("I", msg));
            return "redirect:/todo/" + todo.getId();
  
        } else {
            // エラーあり -> エラーメッセージをセット
            String msg = messageSource.getMessage("msg.e.input_something_wrong", null, locale);
            model.addAttribute("msg", new OpMsg("E", msg));
            return "todoForm";
        }
    }
}
